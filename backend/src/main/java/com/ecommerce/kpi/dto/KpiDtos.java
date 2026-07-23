package com.ecommerce.kpi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class KpiDtos {

    private KpiDtos() {
    }

    // ---------------------------------------------------------------- metas

    public record GoalRequest(
            @NotBlank(message = "Indica la metrica") String metric,
            @NotBlank(message = "Indica a quien se asigna: COMPANY, DEPARTMENT o USER") String targetType,
            /** Nulo para COMPANY; departmentId o userId segun el tipo. */
            String targetId,
            @NotNull(message = "Indica el objetivo")
            @Positive(message = "El objetivo debe ser mayor que cero") BigDecimal target,
            @NotNull(message = "Indica el inicio del periodo") Instant periodStart,
            @NotNull(message = "Indica el fin del periodo") Instant periodEnd) {
    }

    /**
     * Meta con su progreso ya calculado.
     *
     * @param actual   valor real logrado en el periodo, calculado desde los datos
     * @param progress porcentaje 0-100 (o mas, si se supero el objetivo)
     */
    public record GoalView(
            String id,
            String metric,
            String metricLabel,
            String unit,
            String targetType,
            String targetId,
            String targetName,
            BigDecimal target,
            BigDecimal actual,
            double progress,
            boolean achieved,
            Instant periodStart,
            Instant periodEnd) {
    }

    /** Una metrica disponible, para el formulario de creacion de metas. */
    public record MetricOption(String metric, String label, String unit, String scope) {
    }

    /** Un destino posible para una meta: un departamento o un usuario. */
    public record TargetRef(String id, String name, String departmentId) {
    }

    /**
     * Destinos que quien consulta puede fijar como objetivo de una meta. Root ve toda la empresa; un
     * jefe solo sus departamentos y las personas de esos departamentos.
     */
    public record TargetScope(List<TargetRef> departments, List<TargetRef> users) {
    }

    // ---------------------------------------------------------------- panel

    public record StatTile(String label, BigDecimal value, String unit) {
    }

    /** Un punto de una serie: una etiqueta y su valor. */
    public record Slice(String label, BigDecimal value) {
    }

    /** Una serie con nombre, para las graficas de barras o dona. */
    public record Series(String name, String unit, List<Slice> slices) {
    }

    /**
     * Panel de resultados completo.
     *
     * @param tiles       cifras destacadas: ventas, pedidos, casos nuevos/vencidos/en espera
     * @param salesByMonth ventas por mes, para la grafica de linea
     * @param topProducts  productos mas vendidos, por unidades
     * @param casesByStatus reparto de casos por estado
     * @param byDepartment  casos atendidos por departamento
     * @param byAgent       casos atendidos por usuario de empresa
     */
    public record Dashboard(
            List<StatTile> tiles,
            Series salesByMonth,
            Series topProducts,
            Series casesByStatus,
            Series byDepartment,
            Series byAgent) {
    }
}
