package com.ecommerce.security;

/**
 * Permisos granulares por proceso de gestion.
 *
 * <p>Los cargos ({@code roles}) son plantillas que agrupan permisos, y el usuario root puede
 * ademas conceder o revocar permisos individuales sobre cada miembro de su empresa. Los permisos
 * efectivos se calculan en {@link PermissionResolver}.
 *
 * <p>El nombre del enum es la authority que se usa en {@code @PreAuthorize("hasAuthority(...)")}.
 */
public enum Permission {

    // --- Gestion de productos e inventario ---
    PRODUCT_VIEW("Ver productos de la empresa", Group.PRODUCTOS),
    PRODUCT_CREATE("Anadir nuevo producto", Group.PRODUCTOS),
    PRODUCT_UPDATE("Modificar caracteristicas del producto", Group.PRODUCTOS),
    PRODUCT_DELETE("Eliminar producto", Group.PRODUCTOS),
    STOCK_UPDATE("Modificar cantidad en stock", Group.PRODUCTOS),

    // --- Ordenes (etapas 2 y 3) ---
    ORDER_VIEW("Ver pedidos", Group.ORDENES),
    ORDER_STATUS_CHANGE("Cambiar estado del pedido", Group.ORDENES),
    ORDER_ITEMS_CHANGE("Cambiar productos de un pedido", Group.ORDENES),
    REFUND_MANAGE("Gestionar reembolsos", Group.ORDENES),

    // --- Casos de atencion (etapa 4) ---
    CASE_VIEW("Ver casos de usuarios", Group.CASOS),
    CASE_ASSIGN("Asignar casos", Group.CASOS),
    CASE_REPLY("Responder casos", Group.CASOS),

    // --- KPI (etapa 5) ---
    KPI_VIEW_OWN("Ver sus propios KPI", Group.KPI),
    KPI_VIEW_TEAM("Ver KPI de su equipo", Group.KPI),
    KPI_VIEW_ALL("Ver KPI de toda la empresa", Group.KPI),
    KPI_GOAL_MANAGE("Asignar y modificar metas KPI", Group.KPI),

    // --- Administracion de la empresa ---
    USER_MANAGE("Crear, modificar y eliminar usuarios de la empresa", Group.ADMINISTRACION),
    ROLE_MANAGE("Crear, modificar y eliminar cargos", Group.ADMINISTRACION),
    DEPARTMENT_MANAGE("Gestionar departamentos, jefes y equipos", Group.ADMINISTRACION),
    ACTIVITY_VIEW("Visualizar la actividad de los usuarios", Group.ADMINISTRACION),
    COMPANY_SETTINGS("Modificar los datos de la empresa", Group.ADMINISTRACION),

    // --- Comunicacion (etapa 4) ---
    CHAT_GENERAL_POST("Publicar en el chat general", Group.COMUNICACION),
    BROADCAST_SEND("Comunicar informacion a toda la empresa", Group.COMUNICACION);

    /** Agrupacion usada por la interfaz para presentar los permisos al usuario root. */
    public enum Group {
        PRODUCTOS, ORDENES, CASOS, KPI, ADMINISTRACION, COMUNICACION
    }

    private final String label;
    private final Group group;

    Permission(String label, Group group) {
        this.label = label;
        this.group = group;
    }

    public String getLabel() {
        return label;
    }

    public Group getGroup() {
        return group;
    }
}
