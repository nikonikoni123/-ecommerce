package com.ecommerce.cart;

import com.ecommerce.cart.dto.CartDtos.CartLine;
import com.ecommerce.cart.dto.CartDtos.CartView;
import com.ecommerce.cart.dto.CartDtos.CompanyTotals;
import com.ecommerce.cart.dto.CartDtos.DiscountLine;
import com.ecommerce.catalog.Product;
import com.ecommerce.catalog.ProductRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.order.PricingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Carrito de compra.
 *
 * <p>El documento guardado solo tiene identificadores y cantidades: nombres, precios e imagenes se
 * leen del catalogo en cada consulta. Asi el cliente nunca ve un precio caducado ni compra algo que
 * la empresa acaba de retirar, y no hay que sincronizar dos copias del mismo dato.
 */
@Service
public class CartService {

    private final CartRepository carts;
    private final ProductRepository products;
    private final PricingService pricingService;

    public CartService(CartRepository carts, ProductRepository products,
                       PricingService pricingService) {
        this.carts = carts;
        this.products = products;
        this.pricingService = pricingService;
    }

    // ------------------------------------------------------------------ modificacion

    public CartView view(String customerId, boolean randomOrder) {
        return render(load(customerId), customerId, randomOrder);
    }

    public CartView addItem(String customerId, String productId, int quantity) {
        var product = availableProduct(productId);
        var cart = load(customerId);

        var existing = cart.findItem(productId);
        int nueva = existing.map(i -> i.getQuantity() + quantity).orElse(quantity);
        requireStock(product, nueva);

        existing.ifPresentOrElse(
                item -> item.setQuantity(nueva),
                () -> cart.getItems().add(new Cart.CartItem(productId, quantity)));

        return save(cart, customerId);
    }

    /**
     * Anade varias lineas en una sola operacion.
     *
     * <p>Se valida todo el lote antes de tocar el carrito: o entra entero o no entra nada, para que
     * un producto agotado no deje media caja sorpresa a medio anadir.
     */
    public CartView addItems(String customerId, List<Map.Entry<String, Integer>> requested) {
        var cart = load(customerId);

        // Se acumulan las cantidades por producto antes de comprobar el stock, por si el lote
        // repite el mismo producto en varias lineas.
        var deseado = new LinkedHashMap<String, Integer>();
        for (var entrada : requested) {
            deseado.merge(entrada.getKey(), entrada.getValue(), Integer::sum);
        }

        for (var entrada : deseado.entrySet()) {
            var producto = availableProduct(entrada.getKey());
            int yaEnCarrito = cart.findItem(entrada.getKey()).map(Cart.CartItem::getQuantity).orElse(0);
            requireStock(producto, yaEnCarrito + entrada.getValue());
        }

        for (var entrada : deseado.entrySet()) {
            cart.findItem(entrada.getKey()).ifPresentOrElse(
                    item -> item.setQuantity(item.getQuantity() + entrada.getValue()),
                    () -> cart.getItems().add(new Cart.CartItem(entrada.getKey(), entrada.getValue())));
        }

        return save(cart, customerId);
    }

    public CartView updateQuantity(String customerId, String productId, int quantity) {
        var cart = load(customerId);
        var item = cart.findItem(productId)
                .orElseThrow(() -> ApiException.notFound("Ese producto no esta en tu carrito."));

        requireStock(availableProduct(productId), quantity);
        item.setQuantity(quantity);

        return save(cart, customerId);
    }

    /**
     * Sustituye un producto por otro conservando su posicion en la lista, que es lo que espera el
     * cliente al "modificar el producto a solicitar".
     */
    public CartView replaceItem(String customerId, String productId, String newProductId,
                                int quantity) {
        var cart = load(customerId);
        if (indexOf(cart, productId) < 0) {
            throw ApiException.notFound("Ese producto no esta en tu carrito.");
        }

        requireStock(availableProduct(newProductId), quantity);

        // Se quitan ambos y luego se inserta, para no trabajar con indices que se desplazan:
        // si el producto nuevo ya estaba en otra linea, las dos se fusionan en una sola.
        var items = cart.getItems();
        int posicion = indexOf(cart, productId);
        if (!newProductId.equals(productId)) {
            int posicionNuevo = indexOf(cart, newProductId);
            if (posicionNuevo >= 0 && posicionNuevo < posicion) {
                posicion--;   // al quitar una linea anterior, la nuestra sube un puesto
            }
            cart.removeItem(newProductId);
        }
        cart.removeItem(productId);

        items.add(Math.min(Math.max(posicion, 0), items.size()),
                new Cart.CartItem(newProductId, quantity));

        return save(cart, customerId);
    }

    public CartView removeItem(String customerId, String productId) {
        var cart = load(customerId);
        cart.removeItem(productId);
        return save(cart, customerId);
    }

    public void clear(String customerId) {
        carts.findByCustomerId(customerId).ifPresent(cart -> {
            cart.getItems().clear();
            cart.setUpdatedAt(Instant.now());
            carts.save(cart);
        });
    }

    public int totalUnits(String customerId) {
        return carts.findByCustomerId(customerId).map(Cart::totalUnits).orElse(0);
    }

    // ------------------------------------------------------------------ apoyo

    /** Carga el carrito del cliente, o crea uno vacio en memoria si aun no tiene. */
    public Cart load(String customerId) {
        return carts.findByCustomerId(customerId).orElseGet(() -> new Cart(customerId));
    }

    private CartView save(Cart cart, String customerId) {
        cart.setUpdatedAt(Instant.now());
        return render(carts.save(cart), customerId, false);
    }

    private int indexOf(Cart cart, String productId) {
        var items = cart.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getProductId().equals(productId)) {
                return i;
            }
        }
        return -1;
    }

    private Product availableProduct(String productId) {
        return products.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound(
                        "El producto no existe o ya no esta disponible."));
    }

    private void requireStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw ApiException.badRequest("INSUFFICIENT_STOCK",
                    "Solo quedan %d unidades de \"%s\".".formatted(
                            product.getStock(), product.getName()));
        }
    }

    // ------------------------------------------------------------------ presentacion

    /**
     * Monta la vista del carrito con datos vivos y la cotizacion.
     *
     * <p>Los totales se calculan <b>por empresa</b>, porque al pagar el carrito se divide en un
     * pedido por vendedor y el envio se cobra una vez a cada uno. El total global es la suma.
     */
    private CartView render(Cart cart, String customerId, boolean randomOrder) {
        if (cart.isEmpty()) {
            return CartView.empty("COP");
        }

        var lines = new ArrayList<CartLine>();
        var porEmpresa = new LinkedHashMap<String, List<PricingService.Line>>();
        var nombreEmpresa = new LinkedHashMap<String, String>();
        boolean blocked = false;
        String currency = "COP";

        for (var item : cart.getItems()) {
            var producto = products.findById(item.getProductId()).orElse(null);

            if (producto == null || !producto.isActive()) {
                // Se muestra la linea marcada como no disponible en vez de borrarla en silencio:
                // el cliente debe entender por que no puede pagar.
                lines.add(new CartLine(item.getProductId(), "Producto no disponible", null, null,
                        null, null, BigDecimal.ZERO, item.getQuantity(), BigDecimal.ZERO,
                        currency, false, 0));
                blocked = true;
                continue;
            }

            currency = producto.getCurrency();
            boolean hayStock = producto.getStock() >= item.getQuantity();
            blocked = blocked || !hayStock;

            var lineTotal = PricingService.lineTotal(producto.getPrice(), item.getQuantity());

            lines.add(new CartLine(
                    producto.getId(), producto.getName(), producto.getSlug(),
                    producto.getImages().isEmpty() ? null : producto.getImages().get(0),
                    producto.getCompanyId(), producto.getCompanyName(),
                    producto.getPrice(), item.getQuantity(), lineTotal,
                    producto.getCurrency(), hayStock, producto.getStock()));

            if (hayStock) {
                porEmpresa.computeIfAbsent(producto.getCompanyId(), k -> new ArrayList<>())
                        .add(new PricingService.Line(producto.getId(), producto.getPrice(),
                                item.getQuantity()));
                nombreEmpresa.putIfAbsent(producto.getCompanyId(), producto.getCompanyName());
            }
        }

        Instant ahora = Instant.now();
        var totalesEmpresa = new ArrayList<CompanyTotals>();
        var desglose = new LinkedHashMap<String, DiscountLine>();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal descuento = BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal envio = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal porcentaje = BigDecimal.ZERO;
        BigDecimal tipoIva = BigDecimal.ZERO;
        boolean capped = false;
        boolean promocionActiva = false;

        for (var entrada : porEmpresa.entrySet()) {
            var quote = pricingService.quote(entrada.getValue(), customerId, randomOrder, ahora);

            totalesEmpresa.add(new CompanyTotals(
                    entrada.getKey(), nombreEmpresa.get(entrada.getKey()),
                    quote.subtotal(), quote.discount().amount(), quote.taxableBase(),
                    quote.taxAmount(), quote.shippingCost(), quote.total()));

            subtotal = subtotal.add(quote.subtotal());
            descuento = descuento.add(quote.discount().amount());
            base = base.add(quote.taxableBase());
            iva = iva.add(quote.taxAmount());
            envio = envio.add(quote.shippingCost());
            total = total.add(quote.total());

            porcentaje = quote.discount().percent();
            tipoIva = quote.taxRate();
            capped = capped || quote.discount().capped();
            promocionActiva = promocionActiva || !quote.discount().isEmpty();

            // El desglose es el mismo para todas las empresas; se acumulan los importes.
            for (var l : quote.discount().lines()) {
                desglose.merge(l.code(),
                        new DiscountLine(l.code(), l.label(), l.percent(), l.amount()),
                        (a, b) -> new DiscountLine(a.code(), a.label(), a.percent(),
                                a.amount().add(b.amount())));
            }
        }

        return new CartView(lines, totalesEmpresa, List.copyOf(desglose.values()),
                subtotal, porcentaje, descuento, base, tipoIva, iva, envio, total,
                currency, cart.totalUnits(), randomOrder, blocked, capped, promocionActiva);
    }
}
