package com.ecommerce.order;

import com.ecommerce.catalog.Product;
import com.ecommerce.catalog.ProductRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.mail.MailService;
import com.ecommerce.notification.Notification;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.order.dto.CompanyOrderDtos.ChangeItemsRequest;
import com.ecommerce.order.dto.CompanyOrderDtos.OrderItemChange;
import com.ecommerce.order.dto.OrderDtos.OrderDetail;
import com.ecommerce.order.dto.OrderMapper;
import com.ecommerce.security.AppPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Modificacion de pedidos por parte de la empresa: estado y productos.
 *
 * <p>La especificacion es tajante: <i>"cualquier modificacion debe ser notificada al usuario por
 * correo y por notificacion"</i>. Por eso todo cambio pasa por aqui y termina avisando, en lugar de
 * dejar el aviso a criterio de cada controlador.
 */
@Service
public class OrderManagementService {

    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);
    private static final int ESCALA = 2;

    private final OrderRepository orders;
    private final ProductRepository products;
    private final StockService stockService;
    private final PricingService pricingService;
    private final CompanyOrderService companyOrders;
    private final MailService mailService;
    private final NotificationService notifications;

    public OrderManagementService(OrderRepository orders, ProductRepository products,
                                  StockService stockService, PricingService pricingService,
                                  CompanyOrderService companyOrders, MailService mailService,
                                  NotificationService notifications) {
        this.orders = orders;
        this.products = products;
        this.stockService = stockService;
        this.pricingService = pricingService;
        this.companyOrders = companyOrders;
        this.mailService = mailService;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ estado

    public OrderDetail changeStatus(AppPrincipal actor, String orderId, String targetStatus,
                                    String note) {
        var order = companyOrders.require(actor, orderId);
        var destino = parse(targetStatus);

        if (order.getStatus() == destino) {
            throw ApiException.badRequest("SAME_STATUS", "El pedido ya esta en ese estado.");
        }
        if (!order.getStatus().canTransitionTo(destino)) {
            throw ApiException.badRequest("INVALID_TRANSITION",
                    "No se puede pasar de \"%s\" a \"%s\".".formatted(
                            order.getStatus().getLabel(), destino.getLabel()));
        }

        // Cancelar devuelve la mercancia al catalogo: si el pedido no se completa, esas unidades
        // vuelven a estar disponibles.
        if (destino.restoresStock()) {
            devolverStock(order);
        }

        order.pushStatus(destino, actor.userId(), note);
        orders.save(order);

        companyOrders.recordActivity(actor, "ORDER_STATUS_CHANGE", order.getId(),
                Map.of("numero", order.getNumber(), "estado", destino.name()));

        avisar(order,
                "Tu pedido %s ahora esta \"%s\"".formatted(order.getNumber(), destino.getLabel()),
                note == null || note.isBlank()
                        ? "El estado de tu pedido a %s cambio a \"%s\".".formatted(
                                order.getCompanyName(), destino.getLabel())
                        : "El estado de tu pedido a %s cambio a \"%s\". Nota del vendedor: %s"
                                .formatted(order.getCompanyName(), destino.getLabel(), note));

        return OrderMapper.toDetail(order);
    }

    // ------------------------------------------------------------------ productos

    /**
     * Sustituye las lineas del pedido.
     *
     * <p>Se ajusta el stock de lo que entra y sale, se recalculan los importes <b>conservando los
     * porcentajes de descuento originales</b> —el cliente compro con las condiciones de aquel
     * momento, no con las de hoy— y la diferencia queda registrada como saldo. No se simula ningun
     * cobro adicional: solo se deja constancia para que la factura cuadre con lo que se envia.
     */
    public OrderDetail changeItems(AppPrincipal actor, String orderId, ChangeItemsRequest request) {
        var order = companyOrders.require(actor, orderId);

        if (!order.getStatus().allowsItemChanges()) {
            throw ApiException.badRequest("ITEMS_LOCKED",
                    "Un pedido en estado \"%s\" ya no admite cambios de productos."
                            .formatted(order.getStatus().getLabel()));
        }

        var deseado = consolidar(request.items());
        var actual = new LinkedHashMap<String, Integer>();
        order.getItems().forEach(i -> actual.merge(i.getProductId(), i.getQuantity(), Integer::sum));

        // 1. Reservar primero lo que aumenta, para no liberar nada si luego falta stock.
        var reservado = new ArrayList<Map.Entry<String, Integer>>();
        try {
            for (var entrada : deseado.entrySet()) {
                int delta = entrada.getValue() - actual.getOrDefault(entrada.getKey(), 0);
                if (delta > 0) {
                    var producto = productoDisponible(entrada.getKey());
                    if (!stockService.reserve(entrada.getKey(), delta)) {
                        throw ApiException.conflict("INSUFFICIENT_STOCK",
                                "No hay unidades suficientes de \"%s\".".formatted(producto.getName()));
                    }
                    reservado.add(Map.entry(entrada.getKey(), delta));
                }
            }
        } catch (RuntimeException e) {
            reservado.forEach(r -> stockService.release(r.getKey(), r.getValue()));
            throw e;
        }

        // 2. Devolver lo que se reduce o desaparece.
        for (var entrada : actual.entrySet()) {
            int delta = entrada.getValue() - deseado.getOrDefault(entrada.getKey(), 0);
            if (delta > 0) {
                stockService.release(entrada.getKey(), delta);
            }
        }

        // 3. Reconstruir las lineas con los datos vivos del catalogo.
        var nuevasLineas = new ArrayList<Order.OrderItem>();
        for (var entrada : deseado.entrySet()) {
            var p = productoDisponible(entrada.getKey());
            nuevasLineas.add(new Order.OrderItem(
                    p.getId(), p.getName(), p.getSlug(),
                    p.getImages().isEmpty() ? null : p.getImages().get(0),
                    p.getPrice(), entrada.getValue(),
                    PricingService.lineTotal(p.getPrice(), entrada.getValue())));
        }

        BigDecimal totalAnterior = order.getTotal();
        order.setItems(nuevasLineas);
        recalcular(order);

        // Saldo: positivo si el pedido abarato (a favor del cliente), negativo si encarecio.
        BigDecimal diferencia = totalAnterior.subtract(order.getTotal())
                .setScale(ESCALA, RoundingMode.HALF_UP);
        order.setAdjustmentBalance(order.getAdjustmentBalance().add(diferencia));

        order.setUpdatedAt(Instant.now());
        order.getStatusHistory().add(new Order.StatusChange(order.getStatus(), Instant.now(),
                actor.userId(), notaDeCambio(request.note(), diferencia, order.getCurrency())));
        orders.save(order);

        companyOrders.recordActivity(actor, "ORDER_ITEMS_CHANGE", order.getId(),
                Map.of("numero", order.getNumber(), "diferencia", diferencia.toPlainString()));

        avisar(order,
                "Cambios en tu pedido %s".formatted(order.getNumber()),
                "%s modifico los productos de tu pedido. %s%s".formatted(
                        order.getCompanyName(),
                        descripcionSaldo(diferencia, order.getCurrency()),
                        request.note() == null || request.note().isBlank()
                                ? "" : " Nota del vendedor: " + request.note()));

        return OrderMapper.toDetail(order);
    }

    // ------------------------------------------------------------------ apoyo compartido

    /** Devuelve al catalogo todas las unidades del pedido. */
    void devolverStock(Order order) {
        order.getItems().forEach(i -> stockService.release(i.getProductId(), i.getQuantity()));
        log.info("Stock devuelto al catalogo por el pedido {}", order.getNumber());
    }

    /**
     * Recalcula importes conservando el porcentaje de descuento con el que se compro.
     *
     * <p>Volver a consultar el motor de descuentos daria un resultado distinto si la promocion ya
     * cerro, y el cliente perderia una rebaja que ya habia ganado.
     */
    void recalcular(Order order) {
        BigDecimal subtotal = order.getItems().stream()
                .map(Order.OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        BigDecimal porcentaje = order.getDiscountPercent();
        BigDecimal descuento = subtotal.multiply(porcentaje)
                .divide(new BigDecimal("100"), ESCALA, RoundingMode.HALF_UP);

        BigDecimal base = subtotal.subtract(descuento).max(BigDecimal.ZERO)
                .setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal iva = base.multiply(order.getTaxRate())
                .divide(new BigDecimal("100"), ESCALA, RoundingMode.HALF_UP);
        BigDecimal envio = pricingService.shippingFor(base);

        // El desglose se reparte de nuevo sobre el subtotal nuevo, manteniendo los porcentajes.
        var desglose = order.getDiscounts().stream()
                .map(d -> new Order.AppliedDiscount(d.getCode(), d.getLabel(), d.getPercent(),
                        subtotal.multiply(d.getPercent())
                                .divide(new BigDecimal("100"), ESCALA, RoundingMode.HALF_UP)))
                .toList();

        order.setSubtotal(subtotal);
        order.setDiscounts(desglose);
        order.setDiscountAmount(descuento);
        order.setTaxableBase(base);
        order.setTaxAmount(iva);
        order.setShippingCost(envio);
        order.setTotal(base.add(iva).add(envio).setScale(ESCALA, RoundingMode.HALF_UP));
    }

    /** Aviso al cliente por los dos canales que exige la especificacion. */
    void avisar(Order order, String titulo, String cuerpo) {
        notifications.push(order.getCustomerId(), Notification.Type.ORDER, titulo, cuerpo,
                "/pedidos/" + order.getId());

        mailService.sendNotice(order.getCustomerEmail(), order.getCustomerName(),
                titulo, titulo, cuerpo);
    }

    private Map<String, Integer> consolidar(List<OrderItemChange> items) {
        var mapa = new LinkedHashMap<String, Integer>();
        items.forEach(i -> mapa.merge(i.productId(), i.quantity(), Integer::sum));
        return mapa;
    }

    private Product productoDisponible(String productId) {
        return products.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.badRequest("PRODUCT_UNAVAILABLE",
                        "Uno de los productos indicados ya no esta disponible."));
    }

    private String notaDeCambio(String nota, BigDecimal diferencia, String moneda) {
        String base = "Productos modificados. " + descripcionSaldo(diferencia, moneda);
        return nota == null || nota.isBlank() ? base : base + " " + nota;
    }

    private String descripcionSaldo(BigDecimal diferencia, String moneda) {
        int signo = diferencia.compareTo(BigDecimal.ZERO);
        if (signo == 0) {
            return "El importe del pedido no varia.";
        }
        return signo > 0
                ? "Queda un saldo a tu favor de %s %s.".formatted(diferencia.toPlainString(), moneda)
                : "El pedido encarecio en %s %s.".formatted(diferencia.abs().toPlainString(), moneda);
    }

    private OrderStatus parse(String value) {
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("INVALID_STATUS", "Ese estado de pedido no existe.");
        }
    }
}
