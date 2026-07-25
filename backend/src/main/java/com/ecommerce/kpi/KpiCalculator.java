package com.ecommerce.kpi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.ecommerce.catalog.Product;
import com.ecommerce.order.Order;
import com.ecommerce.order.OrderStatus;
import com.ecommerce.support.CaseStatus;
import com.ecommerce.support.SupportCase;

/**
 * Calcula el valor real de cada metrica KPI desde los datos que ya existen: pedidos y casos.
 *
 * <p>Nada de esto se almacena en la meta: se computa al consultar, sobre el periodo pedido y el
 * ambito indicado. Asi el numero siempre refleja el estado real, y no una foto que habria que
 * mantener sincronizada con cada cambio de pedido o de caso.
 *
 * <p>Las metricas de gestion (casos, pedidos) pueden acotarse a una persona o a un departamento; las
 * ventas, por decision de diseno, solo tienen sentido a nivel de empresa o departamento, porque
 * quien compra es el cliente y no el personal.
 */
@Service
public class KpiCalculator {

    private final MongoTemplate mongo;

    public KpiCalculator(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Valor logrado de una metrica en un periodo, para el ambito dado.
     *
     * @param userIdsInScope usuarios a los que se atribuye la metrica de gestion; nulo o vacio = toda
     *                       la empresa
     */
    public BigDecimal actual(String companyId, KpiMetric metric, Instant from, Instant to,
                             List<String> userIdsInScope) {
        return switch (metric) {
            case VENTAS -> ventas(companyId, from, to);
            case PEDIDOS_ENTREGADOS -> pedidosEntregados(companyId, from, to);
            case CASOS_RESUELTOS -> casos(companyId, from, to, userIdsInScope,
                    List.of(CaseStatus.RESUELTO, CaseStatus.CERRADO), false);
            case CASOS_A_TIEMPO -> casos(companyId, from, to, userIdsInScope,
                    List.of(CaseStatus.RESUELTO, CaseStatus.CERRADO), true);
            case CASOS_ATENDIDOS -> casosAtendidos(companyId, from, to, userIdsInScope);
            case PRODUCTOS_ACTIVOS -> productosActivos(companyId);
            case UNIDADES_VENDIDAS -> unidadesPorEstado(companyId, from, to, null); // Todas menos anuladas
            case UNIDADES_EN_CAMINO -> unidadesPorEstado(companyId, from, to, List.of(OrderStatus.ENVIANDO));
            case UNIDADES_REEMBOLSADAS -> unidadesPorEstado(companyId, from, to, List.of(OrderStatus.REEMBOLSADO));
            default -> BigDecimal.ZERO;
        };
    }

    // ------------------------------------------------------------------ ventas y pedidos

    /** Ingresos de los pedidos no anulados creados en el periodo. */
    public BigDecimal ventas(String companyId, Instant from, Instant to) {
        var pedidos = mongo.find(pagados(companyId, from, to), Order.class);
        return pedidos.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal pedidosEntregados(String companyId, Instant from, Instant to) {
        var query = base(companyId, from, to)
                .addCriteria(Criteria.where("status").is(OrderStatus.ENTREGADO));
        return BigDecimal.valueOf(mongo.count(query, Order.class));
    }

    // ------------------------------------------------------------------ casos

    private BigDecimal casos(String companyId, Instant from, Instant to, List<String> userIds,
                             List<CaseStatus> estados, boolean soloATiempo) {
        var casos = mongo.find(casosBase(companyId, from, to, userIds)
                .addCriteria(Criteria.where("status").in(estados)), SupportCase.class);

        long total = soloATiempo
                ? casos.stream().filter(this::resueltoATiempo).count()
                : casos.size();
        return BigDecimal.valueOf(total);
    }

    private BigDecimal casosAtendidos(String companyId, Instant from, Instant to,
                                      List<String> userIds) {
        var query = casosBase(companyId, from, to, userIds)
                .addCriteria(Criteria.where("assignedToUserId").ne(null));
        return BigDecimal.valueOf(mongo.count(query, SupportCase.class));
    }

    /** Un caso se resolvio a tiempo si su ultimo cambio quedo dentro del vencimiento. */
    private boolean resueltoATiempo(SupportCase c) {
        if (c.getDueDate() == null) {
            return true;   // sin SLA, no se cuenta como fuera de plazo
        }
        return !c.getUpdatedAt().isAfter(c.getDueDate());
    }

    // ------------------------------------------------------------------ productos

    private BigDecimal productosActivos(String companyId) {
        var query = new Query(Criteria.where("companyId").is(companyId).and("active").is(true));
        return BigDecimal.valueOf(mongo.count(query, Product.class));
    }

    private BigDecimal unidadesPorEstado(String companyId, Instant from, Instant to, List<OrderStatus> estados) {
        var query = new Query(Criteria.where("companyId").is(companyId)
                .and("createdAt").gte(from).lte(to));
        
        if (estados != null) {
            query.addCriteria(Criteria.where("status").in(estados));
        } else {
            query.addCriteria(Criteria.where("status").ne(OrderStatus.CANCELADO));
        }

        var pedidos = mongo.find(query, Order.class);
        int totalUnidades = pedidos.stream()
                .flatMap(o -> o.getItems().stream())
                .mapToInt(Order.OrderItem::getQuantity)
                .sum();
                
        return BigDecimal.valueOf(totalUnidades);
    }

    // ------------------------------------------------------------------ consultas base

    private Query base(String companyId, Instant from, Instant to) {
        return new Query(Criteria.where("companyId").is(companyId)
                .and("createdAt").gte(from).lte(to));
    }

    private Query pagados(String companyId, Instant from, Instant to) {
        return base(companyId, from, to)
                .addCriteria(Criteria.where("status").nin(
                        List.of(OrderStatus.CANCELADO, OrderStatus.REEMBOLSADO)));
    }

    private Query casosBase(String companyId, Instant from, Instant to, List<String> userIds) {
        var query = new Query(Criteria.where("companyId").is(companyId)
                .and("createdAt").gte(from).lte(to));
        if (userIds != null && !userIds.isEmpty()) {
            query.addCriteria(Criteria.where("assignedToUserId").in(userIds));
        }
        return query;
    }
}
