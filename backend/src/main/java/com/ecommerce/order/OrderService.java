package com.ecommerce.order;

import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.order.dto.OrderDtos.OrderDetail;
import com.ecommerce.order.dto.OrderDtos.OrderSummary;
import com.ecommerce.order.dto.OrderMapper;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Consulta de los pedidos del cliente: en proceso, historial y detalle con estado de entrega. */
@Service
public class OrderService {

    /** Estados que mantienen el pedido "en proceso"; el resto son historial. */
    private static final List<OrderStatus> EN_PROCESO = Arrays.stream(OrderStatus.values())
            .filter(s -> !s.isTerminal())
            .toList();

    private static final List<OrderStatus> HISTORIAL = Arrays.stream(OrderStatus.values())
            .filter(OrderStatus::isTerminal)
            .toList();

    private final OrderRepository orders;

    /**
     * Se inyecta de forma perezosa para romper el ciclo: RefundService necesita OrderService para
     * cargar el pedido del cliente, y este necesita aquel solo para saber si admite reembolso.
     */
    private final org.springframework.beans.factory.ObjectProvider<RefundService> refundService;

    public OrderService(OrderRepository orders,
                        org.springframework.beans.factory.ObjectProvider<RefundService> refundService) {
        this.orders = orders;
        this.refundService = refundService;
    }

    /**
     * @param scope {@code active} para los pedidos en curso, {@code history} para los cerrados,
     *              cualquier otro valor para todos
     */
    public PageResponse<OrderSummary> list(String customerId, String scope, int page, int size) {
        var pageable = PageRequest.of(page, size);

        var result = switch (scope == null ? "" : scope) {
            case "active" -> orders.findByCustomerIdAndStatusInOrderByCreatedAtDesc(
                    customerId, EN_PROCESO, pageable);
            case "history" -> orders.findByCustomerIdAndStatusInOrderByCreatedAtDesc(
                    customerId, HISTORIAL, pageable);
            default -> orders.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        };

        return PageResponse.of(result, OrderMapper::toSummary);
    }

    public OrderDetail detail(String customerId, String orderId) {
        var order = require(customerId, orderId);
        boolean elegible = refundService.getObject().isEligible(order);
        return OrderMapper.toDetail(order, elegible);
    }

    /** Carga el pedido comprobando que pertenece a quien lo pide. */
    public Order require(String customerId, String orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("El pedido no existe."));
    }

    public long countFor(String customerId) {
        return orders.countByCustomerId(customerId);
    }
}
