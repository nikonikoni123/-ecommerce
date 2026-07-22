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
        Nlp nlp) {

    public record Jwt(String secret, long accessExpirationMinutes, long refreshExpirationDays) {
    }

    public record Nlp(String url, int timeoutMs) {
    }
}
