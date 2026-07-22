package com.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Solicitud de reembolso de un cliente sobre un pedido ya entregado.
 *
 * <p>Vive en su propia coleccion y no dentro del pedido porque tiene ciclo de vida propio: se pide,
 * se estudia y se resuelve, y conviene poder listarlas y priorizarlas sin recorrer todos los
 * pedidos. El pedido solo cambia de estado cuando la solicitud se aprueba.
 */
@Document("refundRequests")
public class RefundRequest {

    public enum Status {
        PENDIENTE("Pendiente de revision"),
        APROBADA("Aprobada"),
        RECHAZADA("Rechazada");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    private String id;

    private String orderId;
    private String orderNumber;
    private String companyId;

    private String customerId;
    private String customerEmail;
    private String customerName;

    /** Motivo que da el cliente. Es lo que la empresa lee para decidir. */
    private String reason;

    private BigDecimal amount;
    private String currency;

    private Status status = Status.PENDIENTE;

    /** Respuesta de la empresa al aprobar o rechazar. */
    private String resolution;
    private String resolvedByUserId;
    private Instant resolvedAt;

    private Instant createdAt = Instant.now();

    public boolean isPending() {
        return status == Status.PENDIENTE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getResolvedByUserId() {
        return resolvedByUserId;
    }

    public void setResolvedByUserId(String resolvedByUserId) {
        this.resolvedByUserId = resolvedByUserId;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
