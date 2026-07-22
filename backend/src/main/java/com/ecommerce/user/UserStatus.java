package com.ecommerce.user;

public enum UserStatus {
    /** Cuenta operativa. */
    ACTIVE,
    /** Bloqueada por un administrador. Puede reactivarse. */
    SUSPENDED,
    /** Baja solicitada por el usuario. Borrado logico: se conserva para la trazabilidad de pedidos. */
    DELETED
}
