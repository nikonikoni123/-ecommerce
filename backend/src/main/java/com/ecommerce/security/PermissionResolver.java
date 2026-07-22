package com.ecommerce.security;

import com.ecommerce.company.RoleRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserType;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Calcula los permisos efectivos de un usuario.
 *
 * <p>La formula es: (union de los permisos de sus cargos) + permisos concedidos individualmente
 * - permisos revocados individualmente. Esto es lo que permite a root personalizar el acceso de una
 * persona concreta sin tener que duplicar un cargo entero.
 */
@Service
public class PermissionResolver {

    private final RoleRepository roleRepository;

    public PermissionResolver(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Set<Permission> resolve(User user) {
        if (user.getType() != UserType.COMPANY_MEMBER) {
            return Set.of();
        }
        if (user.isRoot()) {
            // El root tiene todos los permisos de su empresa de forma implicita.
            return EnumSet.allOf(Permission.class);
        }

        Set<Permission> effective = EnumSet.noneOf(Permission.class);
        if (!user.getRoleIds().isEmpty()) {
            roleRepository.findByIdIn(user.getRoleIds())
                    .forEach(role -> effective.addAll(role.getPermissions()));
        }
        effective.addAll(user.getExtraPermissions());
        effective.removeAll(user.getRevokedPermissions());
        return effective;
    }
}
