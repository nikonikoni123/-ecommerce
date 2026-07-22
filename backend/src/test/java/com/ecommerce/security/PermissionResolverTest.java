package com.ecommerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.ecommerce.company.Role;
import com.ecommerce.company.RoleRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Los permisos efectivos son la pieza que sostiene todo el control de acceso de la empresa, asi
 * que la formula (cargos + concedidos - revocados) se prueba caso por caso.
 */
@ExtendWith(MockitoExtension.class)
class PermissionResolverTest {

    @Mock
    private RoleRepository roleRepository;

    private PermissionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PermissionResolver(roleRepository);
    }

    @Test
    void customersHaveNoCompanyPermissions() {
        var customer = new User();
        customer.setType(UserType.CUSTOMER);

        assertThat(resolver.resolve(customer)).isEmpty();
    }

    @Test
    void rootHasEveryPermissionWithoutConsultingRoles() {
        var root = new User();
        root.setType(UserType.COMPANY_MEMBER);
        root.setRoot(true);

        assertThat(resolver.resolve(root)).containsExactlyInAnyOrderElementsOf(
                EnumSet.allOf(Permission.class));
    }

    @Test
    void unionsThePermissionsOfEveryAssignedRole() {
        var member = memberWithRoles("r1", "r2");
        when(roleRepository.findByIdIn(anyCollection())).thenReturn(List.of(
                role("r1", Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE),
                role("r2", Permission.ORDER_VIEW)));

        assertThat(resolver.resolve(member)).containsExactlyInAnyOrder(
                Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE, Permission.ORDER_VIEW);
    }

    @Test
    void addsPermissionsGrantedIndividually() {
        var member = memberWithRoles("r1");
        member.setExtraPermissions(EnumSet.of(Permission.PRODUCT_DELETE));
        when(roleRepository.findByIdIn(anyCollection()))
                .thenReturn(List.of(role("r1", Permission.PRODUCT_VIEW)));

        assertThat(resolver.resolve(member))
                .containsExactlyInAnyOrder(Permission.PRODUCT_VIEW, Permission.PRODUCT_DELETE);
    }

    @Test
    void revokedPermissionsWinOverTheOnesGrantedByARole() {
        var member = memberWithRoles("r1");
        member.setRevokedPermissions(EnumSet.of(Permission.PRODUCT_DELETE));
        when(roleRepository.findByIdIn(anyCollection())).thenReturn(List.of(
                role("r1", Permission.PRODUCT_VIEW, Permission.PRODUCT_DELETE)));

        assertThat(resolver.resolve(member)).containsExactly(Permission.PRODUCT_VIEW);
    }

    @Test
    void revocationAlsoOverridesAnIndividualGrant() {
        var member = memberWithRoles("r1");
        member.setExtraPermissions(EnumSet.of(Permission.PRODUCT_DELETE));
        member.setRevokedPermissions(EnumSet.of(Permission.PRODUCT_DELETE));
        when(roleRepository.findByIdIn(anyCollection()))
                .thenReturn(List.of(role("r1", Permission.PRODUCT_VIEW)));

        assertThat(resolver.resolve(member)).containsExactly(Permission.PRODUCT_VIEW);
    }

    @Test
    void memberWithoutRolesHasOnlyWhatWasGrantedIndividually() {
        var member = new User();
        member.setType(UserType.COMPANY_MEMBER);
        member.setCompanyId("c1");
        member.setExtraPermissions(EnumSet.of(Permission.CASE_VIEW));

        assertThat(resolver.resolve(member)).containsExactly(Permission.CASE_VIEW);
    }

    private User memberWithRoles(String... roleIds) {
        var member = new User();
        member.setType(UserType.COMPANY_MEMBER);
        member.setCompanyId("c1");
        member.setRoleIds(Set.of(roleIds));
        return member;
    }

    private Role role(String id, Permission... permissions) {
        var role = new Role();
        role.setId(id);
        role.setCompanyId("c1");
        role.setPermissions(EnumSet.copyOf(Set.of(permissions)));
        return role;
    }
}
