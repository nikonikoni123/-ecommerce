package com.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracion propia de la aplicacion, bajo el prefijo {@code app} de application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        String frontendUrl,
        String mailFrom,
        boolean seedDemoData,
        Jwt jwt,
        Nlp nlp,
        Pricing pricing,
        Orders orders,
        Support support) {

    public record Jwt(String secret, long accessExpirationMinutes, long refreshExpirationDays) {
    }

    public record Nlp(String url, int timeoutMs) {
    }

    /**
     * Parametros economicos. Todo lo que decide cuanto paga el cliente vive aqui, y no repartido
     * como numeros sueltos por el codigo.
     *
     * @param taxRatePercent            IVA aplicado sobre la base ya descontada
     * @param shippingFlatRate          tarifa plana de envio, cobrada una vez por empresa
     * @param freeShippingThreshold     base imponible a partir de la cual el envio no se cobra
     * @param frequentCustomerOrders    pedidos previos necesarios para ser cliente frecuente
     * @param frequentCustomerPercent   descuento adicional del cliente frecuente
     * @param maxDiscountPercent        techo de la suma de descuentos
     */
    public record Pricing(
            java.math.BigDecimal taxRatePercent,
            java.math.BigDecimal shippingFlatRate,
            java.math.BigDecimal freeShippingThreshold,
            int frequentCustomerOrders,
            java.math.BigDecimal frequentCustomerPercent,
            java.math.BigDecimal maxDiscountPercent) {
    }

    /**
     * Plazos del ciclo de vida del pedido.
     *
     * @param deliveryDays   dias comprometidos de entrega; fijan la fecha de vencimiento
     * @param refundDays     plazo para solicitar un reembolso tras la entrega
     * @param dueSoonHours   antelacion con la que un pedido se considera proximo a vencer
     */
    public record Orders(int deliveryDays, int refundDays, int dueSoonHours) {
    }

    /**
     * SLA de los casos de atencion, en horas segun la prioridad que detecta el BERT.
     *
     * <p>Un caso negativo o critico vence antes que una consulta trivial, de modo que la bandeja
     * ponga arriba lo que de verdad no puede esperar.
     */
    public record Support(int slaHighHours, int slaMediumHours, int slaLowHours) {

        /** Horas de SLA para una prioridad; por defecto la de MEDIUM ante un valor inesperado. */
        public int slaHoursFor(String priority) {
            return switch (priority == null ? "" : priority.toUpperCase()) {
                case "HIGH" -> slaHighHours;
                case "LOW" -> slaLowHours;
                default -> slaMediumHours;
            };
        }
    }
}
