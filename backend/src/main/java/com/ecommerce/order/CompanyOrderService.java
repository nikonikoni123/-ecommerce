package com.ecommerce.order;

import com.ecommerce.activity.ActivityService;
import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.config.AppProperties;
import com.ecommerce.order.dto.CompanyOrderDtos.CompanyOrderRow;
import com.ecommerce.order.dto.CompanyOrderDtos.CompanyOrderStats;
import com.ecommerce.order.dto.OrderDtos.OrderDetail;
import com.ecommerce.order.dto.OrderMapper;
import com.ecommerce.security.AppPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Panel de pedidos de la empresa.
 *
 * <p>La especificacion pide ver los pedidos "por orden de prioridad frente a fecha, fecha de
 * vencimiento, cantidad, estado". Se ofrecen esos cuatro criterios mas uno calculado, la
 * **prioridad**, que es el que resuelve la pregunta real de quien gestiona: que atiendo primero.
 */
@Service
public class CompanyOrderService {

    /** Criterios de orden admitidos. Cualquier otro valor cae en la prioridad calculada. */
    private static final List<String> ORDENES = List.of(
            "priority", "date", "dueDate", "units", "status");

    private final OrderRepository orders;
    private final RefundRequestRepository refunds;
    private final MongoTemplate mongo;
    private final ActivityService activityService;
    private final AppProperties properties;

    public CompanyOrderService(OrderRepository orders, RefundRequestRepository refunds,
                               MongoTemplate mongo, ActivityService activityService,
                               AppProperties properties) {
        this.orders = orders;
        this.refunds = refunds;
        this.mongo = mongo;
        this.activityService = activityService;
        this.properties = properties;
    }

    public PageResponse<CompanyOrderRow> list(AppPrincipal principal, String status, String sort,
                                              boolean onlyOverdue, int page, int size) {
        String companyId = companyOf(principal);

        var criteria = new ArrayList<Criteria>();
        criteria.add(Criteria.where("companyId").is(companyId));

        if (status != null && !status.isBlank()) {
            criteria.add(Criteria.where("status").is(parseStatus(status)));
        }
        if (onlyOverdue) {
            criteria.add(Criteria.where("dueDate").lt(Instant.now()));
            criteria.add(Criteria.where("status").nin(terminales()));
        }

        var query = new Query(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));
        long total = mongo.count(query, Order.class);

        // La prioridad no es un campo almacenado, asi que ese orden se resuelve en memoria sobre un
        // conjunto acotado. Los demas criterios se paginan en la base de datos.
        if (esPrioridad(sort)) {
            query.with(Sort.by(Sort.Direction.ASC, "dueDate")).limit(500);
            var candidatos = new ArrayList<>(mongo.find(query, Order.class));
            candidatos.sort(Comparator
                    .comparingInt((Order o) -> prioridadNumerica(o))
                    .thenComparing(o -> o.getDueDate() == null ? Instant.MAX : o.getDueDate()));

            int desde = Math.min(page * size, candidatos.size());
            int hasta = Math.min(desde + size, candidatos.size());
            return PageResponse.of(candidatos.subList(desde, hasta).stream().map(this::toRow).toList(),
                    page, size, total);
        }

        query.with(sortFor(sort)).skip((long) page * size).limit(size);
        var contenido = mongo.find(query, Order.class).stream().map(this::toRow).toList();
        return PageResponse.of(contenido, page, size, total);
    }

    public OrderDetail detail(AppPrincipal principal, String orderId) {
        return OrderMapper.toDetail(require(principal, orderId));
    }

    public CompanyOrderStats stats(AppPrincipal principal) {
        String companyId = companyOf(principal);

        long total = mongo.count(porEmpresa(companyId), Order.class);

        long enCurso = mongo.count(porEmpresa(companyId)
                .addCriteria(Criteria.where("status").nin(terminales())), Order.class);

        long vencidos = mongo.count(porEmpresa(companyId)
                .addCriteria(Criteria.where("dueDate").lt(Instant.now()))
                .addCriteria(Criteria.where("status").nin(terminales())), Order.class);

        long entregados = mongo.count(porEmpresa(companyId)
                .addCriteria(Criteria.where("status").is(OrderStatus.ENTREGADO)), Order.class);

        long reembolsos = refunds.countByCompanyIdAndStatus(companyId, RefundRequest.Status.PENDIENTE);

        return new CompanyOrderStats(total, enCurso, vencidos, entregados, reembolsos);
    }

    /** Carga el pedido comprobando que pertenece a la empresa de quien lo pide. */
    public Order require(AppPrincipal principal, String orderId) {
        return orders.findByIdAndCompanyId(orderId, companyOf(principal))
                .orElseThrow(() -> ApiException.notFound("El pedido no existe en tu empresa."));
    }

    public String companyOf(AppPrincipal principal) {
        if (principal.companyId() == null) {
            throw ApiException.forbidden("NOT_A_COMPANY_MEMBER",
                    "Esta seccion es exclusiva de las cuentas de empresa.");
        }
        return principal.companyId();
    }

    public void recordActivity(AppPrincipal actor, String action, String orderId,
                               java.util.Map<String, String> metadata) {
        activityService.record(actor, action, "Order", orderId, metadata);
    }

    // ------------------------------------------------------------------ apoyo

    private Query porEmpresa(String companyId) {
        return new Query(Criteria.where("companyId").is(companyId));
    }

    private List<OrderStatus> terminales() {
        return java.util.Arrays.stream(OrderStatus.values()).filter(OrderStatus::isTerminal).toList();
    }

    private boolean esPrioridad(String sort) {
        return sort == null || sort.isBlank() || "priority".equals(sort) || !ORDENES.contains(sort);
    }

    private Sort sortFor(String sort) {
        return switch (sort) {
            case "date" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "dueDate" -> Sort.by(Sort.Direction.ASC, "dueDate");
            // Mongo no ordena por un campo calculado, y las unidades viven dentro de las lineas:
            // se aproxima por importe, que crece con la cantidad y si esta almacenado.
            case "units" -> Sort.by(Sort.Direction.DESC, "total");
            case "status" -> Sort.by(Sort.Direction.ASC, "status").and(
                    Sort.by(Sort.Direction.ASC, "dueDate"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    /** 0 vencido, 1 proximo a vencer, 2 normal, 3 ya cerrado. Menor numero, mas urgente. */
    private int prioridadNumerica(Order order) {
        if (order.getStatus().isTerminal()) {
            return 3;
        }
        if (order.isOverdue()) {
            return 0;
        }
        return esProximoAVencer(order) ? 1 : 2;
    }

    private boolean esProximoAVencer(Order order) {
        if (order.getDueDate() == null || order.getStatus().isTerminal()) {
            return false;
        }
        var umbral = Instant.now().plus(properties.orders().dueSoonHours(), ChronoUnit.HOURS);
        return order.getDueDate().isBefore(umbral) && !order.isOverdue();
    }

    private String etiquetaPrioridad(Order order) {
        return switch (prioridadNumerica(order)) {
            case 0 -> "VENCIDO";
            case 1 -> "POR_VENCER";
            case 3 -> "CERRADO";
            default -> "NORMAL";
        };
    }

    private CompanyOrderRow toRow(Order o) {
        return new CompanyOrderRow(
                o.getId(), o.getNumber(), o.getCustomerName(), o.getCustomerEmail(),
                o.getStatus().name(), o.getStatus().getLabel(), o.getStatus().isTerminal(),
                o.totalUnits(), o.getTotal(), o.getCurrency(),
                o.getCreatedAt(), o.getDueDate(), o.isOverdue(), esProximoAVencer(o),
                etiquetaPrioridad(o),
                o.getGift() != null && o.getGift().isGift(), o.isRandomOrder(),
                refunds.existsByOrderIdAndStatus(o.getId(), RefundRequest.Status.PENDIENTE));
    }

    private OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS", "Ese estado de pedido no existe.");
        }
    }
}
