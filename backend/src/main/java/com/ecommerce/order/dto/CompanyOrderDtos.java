package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contratos del panel de pedidos de la empresa y del ciclo de reembolsos. */
public final class CompanyOrderDtos {

    private CompanyOrderDtos() {
    }

    // ---------------------------------------------------------------- listado

    /**
     * Fila del panel.
     *
     * @param priority  urgencia calculada, para ordenar sin que la empresa tenga que interpretar
     *                  fechas: VENCIDO, POR_VENCER o NORMAL
     * @param overdue   la fecha comprometida ya paso y el pedido sigue abierto
     */
    public record CompanyOrderRow(
            String id,
            String number,
            String customerName,
            String customerEmail,
            String status,
            String statusLabel,
            boolean terminal,
            int units,
            BigDecimal total,
            String currency,
            Instant createdAt,
            Instant dueDate,
            boolean overdue,
            boolean dueSoon,
            String priority,
            boolean gift,
            boolean randomOrder,
            boolean hasPendingRefund) {
    }

    /** Contadores del encabezado del panel. */
    public record CompanyOrderStats(
            long total,
            long inProgress,
            long overdue,
            long delivered,
            long pendingRefunds) {
    }

    // ---------------------------------------------------------------- cambios

    public record ChangeStatusRequest(
            @NotBlank(message = "Indica el estado nuevo") String status,
            @Size(max = 500, message = "La nota no puede superar los 500 caracteres") String note) {
    }

    /** Una linea del pedido tras la modificacion. Las que no se envian se eliminan. */
    public record OrderItemChange(
            @NotBlank(message = "Indica el producto") String productId,
            @NotNull(message = "Indica la cantidad")
            @Min(value = 1, message = "La cantidad minima es 1") Integer quantity) {
    }

    public record ChangeItemsRequest(
            @NotEmpty(message = "El pedido debe conservar al menos un producto")
            @Valid List<OrderItemChange> items,
            @Size(max = 500) String note) {
    }

    // ---------------------------------------------------------------- reembolsos

    public record CreateRefundRequest(
            @NotBlank(message = "Explica por que solicitas el reembolso")
            @Size(min = 10, max = 1000, message = "El motivo debe tener entre 10 y 1000 caracteres")
            String reason) {
    }

    public record ResolveRefundRequest(
            @NotNull(message = "Indica si se aprueba o se rechaza") Boolean approve,
            @Size(max = 1000) String resolution) {
    }

    public record RefundView(
            String id,
            String orderId,
            String orderNumber,
            String customerName,
            String customerEmail,
            String reason,
            BigDecimal amount,
            String currency,
            String status,
            String statusLabel,
            String resolution,
            Instant resolvedAt,
            Instant createdAt) {
    }
}
