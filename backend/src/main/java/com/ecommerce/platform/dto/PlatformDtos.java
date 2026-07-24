package com.ecommerce.platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contratos del panel de plataforma: vision transversal por encima de todas las empresas. */
public final class PlatformDtos {

    private PlatformDtos() {
    }

    /** Una empresa vista desde la plataforma, con sus cifras agregadas. */
    public record CompanyOverview(
            String id,
            String name,
            String nit,
            String email,
            boolean verified,
            long members,
            long products,
            long orders,
            BigDecimal sales,
            Instant createdAt) {
    }

    /** Un empleado de una empresa, visto desde la plataforma. */
    public record MemberView(
            String id,
            String name,
            String email,
            String position,
            boolean root,
            String status) {
    }

    /**
     * Reporte tabular generico. Todos los reportes de la plataforma se reducen a esta forma:
     * titulo, subtitulo con el rango, columnas y filas ya formateadas. El mismo objeto alimenta
     * la vista JSON, el CSV y el PDF, de modo que los tres canales nunca discrepan.
     */
    public record Report(
            String key,
            String title,
            String subtitle,
            List<String> columns,
            List<List<String>> rows,
            Instant generatedAt) {
    }
}
