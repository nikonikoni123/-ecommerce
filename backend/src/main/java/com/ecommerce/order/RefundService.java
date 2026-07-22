package com.ecommerce.order;

import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.config.AppProperties;
import com.ecommerce.notification.Notification;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.order.dto.CompanyOrderDtos.RefundView;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Ciclo de vida del reembolso: el cliente lo solicita y la empresa lo resuelve.
 *
 * <p>Solo se admite sobre pedidos <b>ya entregados</b> y dentro de un plazo configurable. La
 * especificacion lo justifica en que "el producto no era lo esperado", y para saberlo hay que
 * haberlo recibido; antes de la entrega la via correcta es la cancelacion.
 */
@Service
public class RefundService {

    private final RefundRequestRepository refunds;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final CompanyOrderService companyOrders;
    private final OrderManagementService management;
    private final NotificationService notifications;
    private final UserRepository users;
    private final AppProperties properties;

    public RefundService(RefundRequestRepository refunds, OrderRepository orders,
                         OrderService orderService, CompanyOrderService companyOrders,
                         OrderManagementService management, NotificationService notifications,
                         UserRepository users, AppProperties properties) {
        this.users = users;
        this.refunds = refunds;
        this.orders = orders;
        this.orderService = orderService;
        this.companyOrders = companyOrders;
        this.management = management;
        this.notifications = notifications;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ cliente

    /** Comprueba si un pedido admite solicitud, para que la interfaz sepa si ofrecer el boton. */
    public boolean isEligible(Order order) {
        if (order.getStatus() != OrderStatus.ENTREGADO) {
            return false;
        }
        if (refunds.existsByOrderIdAndStatus(order.getId(), RefundRequest.Status.PENDIENTE)) {
            return false;
        }
        return !entregaFueraDePlazo(order);
    }

    public RefundView request(AppPrincipal principal, String orderId, String reason) {
        var order = orderService.require(principal.userId(), orderId);

        if (order.getStatus() != OrderStatus.ENTREGADO) {
            throw ApiException.badRequest("NOT_DELIVERED",
                    "Solo puedes pedir el reembolso de un pedido ya entregado. "
                            + "Si aun no llego, contacta con el vendedor para cancelarlo.");
        }
        if (refunds.existsByOrderIdAndStatus(orderId, RefundRequest.Status.PENDIENTE)) {
            throw ApiException.conflict("REFUND_ALREADY_OPEN",
                    "Ya tienes una solicitud de reembolso abierta para este pedido.");
        }
        if (entregaFueraDePlazo(order)) {
            throw ApiException.badRequest("REFUND_WINDOW_CLOSED",
                    "El plazo de %d dias para solicitar el reembolso ya venció."
                            .formatted(properties.orders().refundDays()));
        }

        var solicitud = new RefundRequest();
        solicitud.setOrderId(order.getId());
        solicitud.setOrderNumber(order.getNumber());
        solicitud.setCompanyId(order.getCompanyId());
        solicitud.setCustomerId(order.getCustomerId());
        solicitud.setCustomerEmail(order.getCustomerEmail());
        solicitud.setCustomerName(order.getCustomerName());
        solicitud.setReason(reason.trim());
        solicitud.setAmount(order.getTotal());
        solicitud.setCurrency(order.getCurrency());
        solicitud = refunds.save(solicitud);

        // El aviso va a la empresa: es quien tiene que actuar.
        var destinatario = companyRecipient(order);
        final var creada = solicitud;
        destinatario.ifPresent(userId -> notifications.push(userId, Notification.Type.ORDER,
                "Solicitud de reembolso del pedido %s".formatted(order.getNumber()),
                "%s pide el reembolso: %s".formatted(order.getCustomerName(), creada.getReason()),
                "/empresa/reembolsos"));

        return toView(solicitud);
    }

    public List<RefundView> mine(AppPrincipal principal) {
        return refunds.findByCustomerIdOrderByCreatedAtDesc(principal.userId()).stream()
                .map(this::toView)
                .toList();
    }

    // ------------------------------------------------------------------ empresa

    public PageResponse<RefundView> list(AppPrincipal principal, String status, int page, int size) {
        String companyId = companyOrders.companyOf(principal);
        var pageable = PageRequest.of(page, size);

        var resultado = (status == null || status.isBlank())
                ? refunds.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                : refunds.findByCompanyIdAndStatusOrderByCreatedAtDesc(
                        companyId, parse(status), pageable);

        return PageResponse.of(resultado, this::toView);
    }

    /**
     * Resuelve la solicitud. Al aprobarla el pedido pasa a REEMBOLSADO y la mercancia vuelve al
     * catalogo; al rechazarla el pedido se queda como esta.
     */
    public RefundView resolve(AppPrincipal actor, String refundId, boolean approve,
                              String resolution) {
        String companyId = companyOrders.companyOf(actor);

        var solicitud = refunds.findByIdAndCompanyId(refundId, companyId)
                .orElseThrow(() -> ApiException.notFound("La solicitud no existe en tu empresa."));

        if (!solicitud.isPending()) {
            throw ApiException.badRequest("ALREADY_RESOLVED",
                    "Esta solicitud ya se resolvio como \"%s\".".formatted(
                            solicitud.getStatus().getLabel()));
        }

        solicitud.setStatus(approve ? RefundRequest.Status.APROBADA : RefundRequest.Status.RECHAZADA);
        solicitud.setResolution(resolution);
        solicitud.setResolvedByUserId(actor.userId());
        solicitud.setResolvedAt(Instant.now());
        refunds.save(solicitud);

        var order = orders.findById(solicitud.getOrderId())
                .orElseThrow(() -> ApiException.notFound("El pedido asociado ya no existe."));

        if (approve) {
            management.devolverStock(order);
            order.pushStatus(OrderStatus.REEMBOLSADO, actor.userId(),
                    "Reembolso aprobado" + (resolution == null || resolution.isBlank()
                            ? "" : ": " + resolution));
            orders.save(order);
        }

        companyOrders.recordActivity(actor, approve ? "REFUND_APPROVE" : "REFUND_REJECT",
                order.getId(), Map.of("numero", order.getNumber()));

        management.avisar(order,
                approve
                        ? "Reembolso aprobado del pedido %s".formatted(order.getNumber())
                        : "Reembolso rechazado del pedido %s".formatted(order.getNumber()),
                (approve
                        ? "%s aprobo el reembolso de %s %s.".formatted(order.getCompanyName(),
                                solicitud.getAmount().toPlainString(), solicitud.getCurrency())
                        : "%s no acepto la solicitud de reembolso.".formatted(order.getCompanyName()))
                        + (resolution == null || resolution.isBlank() ? "" : " " + resolution));

        return toView(solicitud);
    }

    // ------------------------------------------------------------------ apoyo

    /** Fecha en la que se marco como entregado, o la de creacion si no consta. */
    private Instant fechaDeEntrega(Order order) {
        return order.getStatusHistory().stream()
                .filter(h -> h.getStatus() == OrderStatus.ENTREGADO)
                .map(Order.StatusChange::getAt)
                .reduce((a, b) -> b)
                .orElse(order.getCreatedAt());
    }

    private boolean entregaFueraDePlazo(Order order) {
        var limite = fechaDeEntrega(order).plus(properties.orders().refundDays(), ChronoUnit.DAYS);
        return Instant.now().isAfter(limite);
    }

    /**
     * Destinatario del aviso dentro de la empresa: su usuario root.
     *
     * <p>En la Fase 5, con departamentos y equipos, el aviso podra dirigirse a quien corresponda.
     */
    private Optional<String> companyRecipient(Order order) {
        return users.findByCompanyIdAndRootIsTrue(order.getCompanyId()).map(User::getId);
    }

    private RefundRequest.Status parse(String value) {
        try {
            return RefundRequest.Status.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS", "Ese estado de solicitud no existe.");
        }
    }

    private RefundView toView(RefundRequest r) {
        return new RefundView(r.getId(), r.getOrderId(), r.getOrderNumber(), r.getCustomerName(),
                r.getCustomerEmail(), r.getReason(), r.getAmount(), r.getCurrency(),
                r.getStatus().name(), r.getStatus().getLabel(), r.getResolution(),
                r.getResolvedAt(), r.getCreatedAt());
    }
}
