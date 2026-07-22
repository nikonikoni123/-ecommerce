package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    // ---------------------------------------------------------------- pago

    /** Destinatario alternativo cuando el pedido se envia como regalo. */
    public record GiftRequest(
            @NotBlank(message = "Indica el nombre de quien recibe el regalo")
            @Size(max = 150) String recipientName,

            @NotBlank(message = "Indica la direccion de entrega del regalo")
            @Size(max = 200) String address,

            @NotBlank(message = "Indica el codigo postal de entrega")
            @Size(max = 20) String postalCode,

            @Size(max = 500, message = "El mensaje no puede superar los 500 caracteres")
            String message) {
    }

    /**
     * Datos del pago simulado.
     *
     * <p>No se piden ni se guardan datos de tarjeta: el pago es una simulacion y pedirlos solo
     * anadiria informacion sensible sin ninguna utilidad.
     */
    public record CheckoutRequest(
            @NotBlank(message = "Indica el nombre de quien recibe")
            @Size(max = 150) String recipientName,

            @NotBlank(message = "La direccion de envio es obligatoria")
            @Size(max = 200) String address,

            @NotBlank(message = "El codigo postal es obligatorio")
            @Size(max = 20) String postalCode,

            @NotBlank(message = "El telefono de contacto es obligatorio")
            @Size(max = 30) String phone,

            /** Etiqueta del medio de pago simulado. */
            String paymentMethod,

            /** El pedido procede de la caja sorpresa y opta al descuento correspondiente. */
            boolean randomOrder,

            boolean asGift,

            @Valid GiftRequest gift) {
    }

    /** Resultado del pago: puede haber generado varios pedidos, uno por empresa. */
    public record CheckoutResponse(
            List<OrderSummary> orders,
            BigDecimal grandTotal,
            String currency,
            String paymentReference,
            String message) {
    }

    // ---------------------------------------------------------------- consulta

    public record OrderSummary(
            String id,
            String number,
            String companyId,
            String companyName,
            String status,
            String statusLabel,
            boolean terminal,
            int itemCount,
            BigDecimal total,
            String currency,
            boolean randomOrder,
            boolean gift,
            Instant createdAt) {
    }

    public record OrderItemView(
            String productId,
            String name,
            String slug,
            String imageUrl,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {
    }

    public record DiscountView(String code, String label, BigDecimal percent, BigDecimal amount) {
    }

    public record StatusChangeView(String status, String label, Instant at, String note) {
    }

    public record AddressView(String recipientName, String address, String postalCode, String phone) {
    }

    public record GiftView(boolean isGift, String recipientName, String address, String postalCode,
                           String message) {
    }

    public record PaymentView(boolean simulated, String method, String reference, Instant paidAt) {
    }

    public record OrderDetail(
            String id,
            String number,
            String companyId,
            String companyName,
            String customerName,
            String customerEmail,
            List<OrderItemView> items,
            BigDecimal subtotal,
            List<DiscountView> discounts,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal taxableBase,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal shippingCost,
            BigDecimal total,
            String currency,
            String status,
            String statusLabel,
            boolean terminal,
            List<StatusChangeView> history,
            boolean randomOrder,
            AddressView shipping,
            GiftView gift,
            PaymentView payment,
            Instant createdAt,
            Instant updatedAt) {
    }

    // ---------------------------------------------------------------- caja sorpresa

    /**
     * Peticion de una propuesta sorpresa.
     *
     * @param items    cuantos productos distintos debe traer
     * @param maxTotal presupuesto maximo orientativo, opcional
     */
    public record SurpriseRequest(
            @Min(value = 1, message = "La caja debe traer al menos 1 producto")
            @Max(value = 10, message = "La caja admite como maximo 10 productos")
            Integer items,
            BigDecimal maxTotal,
            String category) {
    }

    /**
     * Propuesta de caja sorpresa. Todavia no compromete nada: el cliente la acepta y pasa al
     * carrito, o pide otra.
     */
    public record SurpriseProposal(
            List<OrderItemView> items,
            BigDecimal subtotal,
            BigDecimal estimatedDiscountPercent,
            BigDecimal estimatedTotal,
            String currency,
            String message) {
    }
}
