package com.ecommerce.order;

import com.ecommerce.cart.CartService;
import com.ecommerce.catalog.Product;
import com.ecommerce.catalog.ProductRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.common.SequenceService;
import com.ecommerce.mail.MailService;
import com.ecommerce.notification.Notification;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.order.dto.OrderDtos.CheckoutRequest;
import com.ecommerce.order.dto.OrderDtos.CheckoutResponse;
import com.ecommerce.order.dto.OrderMapper;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pago simulado y creacion de los pedidos.
 *
 * <p>El carrito se divide en <b>un pedido por empresa</b>: cada vendedor recibe su propio numero,
 * su estado y su factura, de modo que en la Etapa 3 dos empresas no compitan por un mismo campo de
 * estado. El pago, en cambio, es unico y comparte referencia entre todos los pedidos.
 *
 * <p>MongoDB corre como nodo suelto, sin transacciones multidocumento, asi que la atomicidad se
 * consigue a mano: se reserva el stock pieza a pieza y, si algo falla, se devuelve lo ya reservado.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartService cartService;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;
    private final PricingService pricingService;
    private final StockService stockService;
    private final SequenceService sequences;
    private final MailService mailService;
    private final NotificationService notifications;

    public CheckoutService(CartService cartService, ProductRepository products,
                           OrderRepository orders, UserRepository users,
                           PricingService pricingService, StockService stockService,
                           SequenceService sequences, MailService mailService,
                           NotificationService notifications) {
        this.cartService = cartService;
        this.products = products;
        this.orders = orders;
        this.users = users;
        this.pricingService = pricingService;
        this.stockService = stockService;
        this.sequences = sequences;
        this.mailService = mailService;
        this.notifications = notifications;
    }

    public CheckoutResponse checkout(String customerId, CheckoutRequest request) {
        var cart = cartService.load(customerId);
        if (cart.isEmpty()) {
            throw ApiException.badRequest("EMPTY_CART", "Tu carrito esta vacio.");
        }

        var customer = users.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("La cuenta no existe."));

        if (request.asGift() && request.gift() == null) {
            throw ApiException.badRequest("GIFT_DATA_REQUIRED",
                    "Para enviar como regalo hay que indicar destinatario, direccion y codigo postal.");
        }

        // 1. Agrupar por empresa, validando disponibilidad con los datos vivos del catalogo.
        var porEmpresa = new LinkedHashMap<String, List<LineaResuelta>>();
        for (var item : cart.getItems()) {
            var producto = products.findById(item.getProductId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> ApiException.badRequest("PRODUCT_UNAVAILABLE",
                            "Un producto de tu carrito ya no esta disponible. Revisalo antes de pagar."));

            porEmpresa.computeIfAbsent(producto.getCompanyId(), k -> new ArrayList<>())
                    .add(new LineaResuelta(producto, item.getQuantity()));
        }

        // 2. Reservar el stock de todo el carrito antes de crear nada.
        var reservado = new ArrayList<LineaResuelta>();
        try {
            for (var lineas : porEmpresa.values()) {
                for (var linea : lineas) {
                    if (!stockService.reserve(linea.producto().getId(), linea.cantidad())) {
                        throw ApiException.conflict("INSUFFICIENT_STOCK",
                                "Se agotaron las unidades de \"%s\" mientras completabas el pago."
                                        .formatted(linea.producto().getName()));
                    }
                    reservado.add(linea);
                }
            }

            // 3. Crear un pedido por empresa.
            Instant ahora = Instant.now();
            String referencia = "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

            var creados = new ArrayList<Order>();
            for (var entrada : porEmpresa.entrySet()) {
                creados.add(buildOrder(customer, entrada.getValue(), request, referencia, ahora));
            }
            orders.saveAll(creados);

            // 4. El carrito ya cumplio su funcion.
            cartService.clear(customerId);

            notifyCustomer(customer, creados);

            BigDecimal granTotal = creados.stream()
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String mensaje = creados.size() == 1
                    ? "Pago realizado. Tu pedido esta en preparacion."
                    : "Pago realizado. Se generaron %d pedidos, uno por cada empresa vendedora."
                            .formatted(creados.size());

            return new CheckoutResponse(
                    creados.stream().map(OrderMapper::toSummary).toList(),
                    granTotal,
                    creados.get(0).getCurrency(),
                    referencia,
                    mensaje);

        } catch (RuntimeException e) {
            // Compensacion: sin transacciones, devolver lo reservado es responsabilidad nuestra.
            for (var linea : reservado) {
                stockService.release(linea.producto().getId(), linea.cantidad());
            }
            throw e;
        }
    }

    private Order buildOrder(User customer, List<LineaResuelta> lineas, CheckoutRequest request,
                             String referencia, Instant ahora) {
        var primera = lineas.get(0).producto();

        var paraCotizar = lineas.stream()
                .map(l -> new PricingService.Line(l.producto().getId(), l.producto().getPrice(),
                        l.cantidad()))
                .toList();

        var quote = pricingService.quote(paraCotizar, customer.getId(), request.randomOrder(), ahora);

        var order = new Order();
        order.setNumber(sequences.nextOrderNumber());
        order.setCustomerId(customer.getId());
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerName(customer.displayName().trim());
        order.setCompanyId(primera.getCompanyId());
        order.setCompanyName(primera.getCompanyName());
        order.setCurrency(primera.getCurrency());

        order.setItems(lineas.stream()
                .map(l -> new Order.OrderItem(
                        l.producto().getId(), l.producto().getName(), l.producto().getSlug(),
                        l.producto().getImages().isEmpty() ? null : l.producto().getImages().get(0),
                        l.producto().getPrice(), l.cantidad(),
                        PricingService.lineTotal(l.producto().getPrice(), l.cantidad())))
                .toList());

        order.setSubtotal(quote.subtotal());
        order.setDiscounts(quote.discount().lines().stream()
                .map(d -> new Order.AppliedDiscount(d.code(), d.label(), d.percent(), d.amount()))
                .toList());
        order.setDiscountPercent(quote.discount().percent());
        order.setDiscountAmount(quote.discount().amount());
        order.setTaxableBase(quote.taxableBase());
        order.setTaxRate(quote.taxRate());
        order.setTaxAmount(quote.taxAmount());
        order.setShippingCost(quote.shippingCost());
        order.setTotal(quote.total());
        order.setRandomOrder(request.randomOrder());

        order.setShipping(new Order.Address(request.recipientName(), request.address(),
                request.postalCode(), request.phone()));

        var gift = new Order.Gift();
        gift.setGift(request.asGift());
        if (request.asGift()) {
            gift.setRecipientName(request.gift().recipientName());
            gift.setAddress(request.gift().address());
            gift.setPostalCode(request.gift().postalCode());
            gift.setMessage(request.gift().message());
        }
        order.setGift(gift);

        order.setPayment(new Order.Payment(
                request.paymentMethod() == null || request.paymentMethod().isBlank()
                        ? "Tarjeta simulada" : request.paymentMethod(),
                referencia, ahora));

        order.pushStatus(OrderStatus.PREPARANDO_ORDEN, customer.getId(), "Pago confirmado");
        return order;
    }

    /** Aviso por correo y en la aplicacion, uno por pedido creado. */
    private void notifyCustomer(User customer, List<Order> creados) {
        for (var order : creados) {
            notifications.push(customer.getId(), Notification.Type.ORDER,
                    "Pedido %s confirmado".formatted(order.getNumber()),
                    "Tu pedido a %s esta en preparacion.".formatted(order.getCompanyName()),
                    "/pedidos/" + order.getId());

            mailService.sendNotice(customer.getEmail(), customer.displayName().trim(),
                    "Pedido %s confirmado".formatted(order.getNumber()),
                    "Tu pedido %s esta confirmado".formatted(order.getNumber()),
                    "Recibimos tu pago de %s %s para el pedido a %s. Puedes seguir su estado y "
                            .formatted(order.getTotal().toPlainString(), order.getCurrency(),
                                    order.getCompanyName())
                            + "descargar la factura desde tu cuenta.");
        }
        log.info("Checkout completado: {} pedido(s) para {}", creados.size(), customer.getEmail());
    }

    /** Producto con su cantidad, ya validado contra el catalogo. */
    private record LineaResuelta(Product producto, int cantidad) {
    }
}
