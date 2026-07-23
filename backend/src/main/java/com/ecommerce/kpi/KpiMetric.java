package com.ecommerce.kpi;

/**
 * Metricas sobre las que se pueden fijar metas.
 *
 * <p>Cada una sabe si se mide en dinero o en cantidad, y a que ambito tiene sentido asignarla. Las
 * de gestion (casos, pedidos) pueden atribuirse a una persona; las de ventas, por decision de
 * diseno, son de la empresa o del departamento, porque quien compra es el cliente y no el personal.
 */
public enum KpiMetric {

    VENTAS("Ventas", Unit.CURRENCY, Scope.COMPANY_OR_DEPARTMENT),
    PEDIDOS_ENTREGADOS("Pedidos entregados", Unit.COUNT, Scope.ANY),
    CASOS_RESUELTOS("Casos resueltos", Unit.COUNT, Scope.ANY),
    CASOS_A_TIEMPO("Casos resueltos a tiempo", Unit.COUNT, Scope.ANY),
    CASOS_ATENDIDOS("Casos atendidos", Unit.COUNT, Scope.ANY);

    public enum Unit {
        CURRENCY, COUNT
    }

    public enum Scope {
        /** Puede asignarse a usuario, equipo o empresa. */
        ANY,
        /** Solo a empresa o departamento, no a una persona. */
        COMPANY_OR_DEPARTMENT
    }

    private final String label;
    private final Unit unit;
    private final Scope scope;

    KpiMetric(String label, Unit unit, Scope scope) {
        this.label = label;
        this.unit = unit;
        this.scope = scope;
    }

    public String getLabel() {
        return label;
    }

    public Unit getUnit() {
        return unit;
    }

    public Scope getScope() {
        return scope;
    }
}
