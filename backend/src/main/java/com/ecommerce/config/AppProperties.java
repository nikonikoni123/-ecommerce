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
        Pricing pricing) {

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
}
