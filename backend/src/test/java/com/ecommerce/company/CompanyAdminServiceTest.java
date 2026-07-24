package com.ecommerce.company;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ecommerce.activity.ActivityService;
import com.ecommerce.auth.token.VerificationTokenRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.company.dto.AdminDtos.RoleRequest;
import com.ecommerce.mail.MailService;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.PermissionResolver;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserType;

/**
 * La administracion de la empresa protege los invariantes que la especificacion y el modelo exigen:
 * el root no se puede borrar, nadie se borra a si mismo, los cargos del sistema son intocables y no
 * hay dos cargos con el mismo nombre. Esas guardas viven en el servicio y aqui se comprueban.
 */
@ExtendWith(MockitoExtension.class)
class CompanyAdminServiceTest {

    private static final String EMPRESA = "company-1";

    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @Mock private DepartmentRepository departments;
    @Mock private VerificationTokenRepository tokens;
    @Mock private PermissionResolver permissionResolver;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MailService mailService;
    @Mock private ActivityService activityService;

    private CompanyAdminService service;

    @BeforeEach
    void setUp() {
        service = new CompanyAdminService(users, roles, departments, tokens, permissionResolver,
                passwordEncoder, mailService, activityService);
    }

    // ------------------------------------------------------------------ cargos

    @Test
    void noSePuedeCrearUnCargoConNombreRepetido() {
        var existente = new Role();
        existente.setName("Supervisor");
        when(roles.findByCompanyIdAndNameIgnoreCase(EMPRESA, "Supervisor"))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.createRole(root(),
                new RoleRequest("Supervisor", "desc", Set.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Ya existe un cargo");

        verify(roles, never()).save(any());
    }

    @Test
    void noSePuedeEliminarUnCargoDelSistema() {
        var sistema = new Role();
        sistema.setId("r-sys");
        sistema.setCompanyId(EMPRESA);
        sistema.setName("Root");
        sistema.setSystem(true);
        when(roles.findById("r-sys")).thenReturn(Optional.of(sistema));

        assertThatThrownBy(() -> service.deleteRole(root(), "r-sys"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no se pueden eliminar");

        verify(roles, never()).delete(any());
    }

    // ------------------------------------------------------------------ usuarios

    @Test
    void noSePuedeEliminarAlRoot() {
        var elRoot = miembro("root-1", "Root");
        elRoot.setRoot(true);
        when(users.findById("root-1")).thenReturn(Optional.of(elRoot));

        assertThatThrownBy(() -> service.deleteMember(root(), "root-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("root");

        verify(users, never()).saveAll(any());
    }

    @Test
    void nadieSePuedeEliminarASiMismo() {
        var yo = miembro("admin-1", "Admin");
        when(users.findById("admin-1")).thenReturn(Optional.of(yo));

        var actor = new AppPrincipal("admin-1", "admin@test.local", UserType.COMPANY_MEMBER, EMPRESA,false, Set.of());

        assertThatThrownBy(() -> service.deleteMember(actor, "admin-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ti mismo");
    }

    @Test
    void noSePuedeCrearUnMiembroConCorreoYaUsado() {
        when(users.existsByEmailIgnoreCase("beto@test.local")).thenReturn(true);

        var request = new com.ecommerce.company.dto.AdminDtos.CreateMemberRequest(
                "Beto", "beto@test.local", "Analista", null, null, Set.of());

        assertThatThrownBy(() -> service.createMember(root(), request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Ya existe una cuenta");

        verify(users, never()).save(any());
    }

    // ------------------------------------------------------------------ apoyo

    private AppPrincipal root() {
        return new AppPrincipal("root-1", "root@test.local", UserType.COMPANY_MEMBER, EMPRESA, true, Set.of());
    }

    private User miembro(String id, String nombre) {
        var u = new User();
        u.setId(id);
        u.setType(UserType.COMPANY_MEMBER);
        u.setCompanyId(EMPRESA);
        u.setFirstName(nombre);
        return u;
    }
}
