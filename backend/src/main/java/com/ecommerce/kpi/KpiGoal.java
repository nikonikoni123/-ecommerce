package com.ecommerce.kpi;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Meta KPI: un objetivo sobre una metrica, para un periodo y un asignado.
 *
 * <p>La meta guarda solo el objetivo; el valor cumplido no se almacena, se calcula desde los datos
 * reales (pedidos y casos) cada vez que se consulta. Guardar el progreso obligaria a recalcularlo en
 * cada cambio de pedido o caso, y quedaria desincronizado en cuanto algo cambiara por otra via.
 */
@Document("kpiGoals")
public class KpiGoal {

    /** A quien se le asigna la meta. */
    public enum TargetType {
        COMPANY, DEPARTMENT, USER
    }

    @Id
    private String id;

    private String companyId;

    private KpiMetric metric;
    private TargetType targetType;

    /** Identificador del asignado: nulo para COMPANY, departmentId o userId segun el tipo. */
    private String targetId;
    private String targetName;

    private BigDecimal target;

    /** Ventana de la meta. El valor cumplido se calcula sobre los datos de este rango. */
    private Instant periodStart;
    private Instant periodEnd;

    private String createdByUserId;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public KpiMetric getMetric() {
        return metric;
    }

    public void setMetric(KpiMetric metric) {
        this.metric = metric;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(TargetType targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public void setTarget(BigDecimal target) {
        this.target = target;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
