package com.ecommerce.security;

import java.util.Set;

import com.ecommerce.user.UserType;

/**
 * Usuario autenticado tal y como lo ven los controladores, via {@code @AuthenticationPrincipal}.
 *
 * <p>Los permisos no viajan dentro del JWT: se recalculan en cada peticion a partir del usuario,
 * de forma que un cambio de cargo hecho por root surte efecto de inmediato y no al caducar el token.
 */
public record AppPrincipal(
        String userId,
        String email,
        UserType type,
        String companyId,
        boolean root,
        Set<Permission> permissions) {

    public boolean isCustomer() {
        return type == UserType.CUSTOMER;
    }

    public boolean isCompanyMember() {
        return type == UserType.COMPANY_MEMBER;
    }

    public boolean has(Permission permission) {
        return root || permissions.contains(permission);
    }
}
