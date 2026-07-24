package com.ecommerce.chat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.common.ApiException;
import com.ecommerce.company.Department;
import com.ecommerce.company.DepartmentRepository;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.Permission;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserType;

/**
 * El chat general lo lee todo el personal pero solo publican los autorizados, y el comunicado de
 * root tiene que llegar a todos. Se prueban esas dos reglas, que son el corazon de la funcion.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final String EMPRESA = "company-1";

    @Mock private ChatMessageRepository messages;
    @Mock private UserRepository users;
    @Mock private DepartmentRepository departments;
    @Mock private NotificationService notifications;

    private ChatService service;

    @BeforeEach
    void setUp() {
        service = new ChatService(messages, users, departments, notifications);
        lenient().when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(users.findById(anyString())).thenReturn(Optional.of(miembro("autor-1", "Ana")));
    }

    @Test
    void quienNoTienePermisoNoPuedePublicar() {
        var sinPermiso = principal("u1", Set.of());

        assertThatThrownBy(() -> service.post(sinPermiso, "Hola a todos"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permiso");

        verify(messages, never()).save(any());
    }

    @Test
    void quienTienePermisoPublica() {
        var conPermiso = principal("autor-1", Set.of(Permission.CHAT_GENERAL_POST));

        var vista = service.post(conPermiso, "Buenos dias equipo");

        assertThat(vista.body()).isEqualTo("Buenos dias equipo");
        assertThat(vista.type()).isEqualTo("MESSAGE");
        assertThat(vista.mine()).isTrue();
        verify(messages).save(any());
    }

    @Test
    void canPostReflejaElPermiso() {
        assertThat(service.canPost(principal("u", Set.of(Permission.CHAT_GENERAL_POST)))).isTrue();
        assertThat(service.canPost(principal("u", Set.of()))).isFalse();
    }

    @Test
    void elComunicadoDeRootNotificaATodaLaEmpresaMenosAElMismo() {
        var root = principal("root-1", Set.of(Permission.BROADCAST_SEND));
        when(users.findByCompanyId(EMPRESA)).thenReturn(List.of(
                miembro("root-1", "Root"),      // el autor no se notifica a si mismo
                miembro("u2", "Beto"),
                miembro("u3", "Carla")));

        var vista = service.broadcast(root, "Manana cerramos por inventario");

        assertThat(vista.type()).isEqualTo("BROADCAST");
        verify(messages).save(any());
        // Notifica a los dos miembros distintos del autor.
        verify(notifications, times(2)).push(anyString(), any(), anyString(), anyString(), anyString());
        verify(notifications, never()).push(org.mockito.ArgumentMatchers.eq("root-1"), any(),
                anyString(), anyString(), anyString());
    }

    @Test
    void leerElChatNoExigePermisoDePublicacion() {
        var lector = principal("u9", Set.of());
        when(messages.findByCompanyIdAndChannelOrderByCreatedAtDesc(anyString(), anyString(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        // No lanza: cualquier miembro de la empresa puede leer.
        var feed = service.history(lector, 0, 30);
        assertThat(feed.content()).isEmpty();
    }

    @Test
    void unUsuarioSinEmpresaNoAccedeAlChat() {
        var cliente = new AppPrincipal("c1", "c@test.local", UserType.CUSTOMER, null, false, Set.of());

        assertThatThrownBy(() -> service.history(cliente, 0, 30))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empresa");
    }

    // ------------------------------------------------------------------ chat por departamento

    @Test
    void unMiembroPublicaEnElChatDeSuDepartamento() {
        var dept = departamento("d1", "Soporte", "jefe-1");
        when(departments.findByIdAndCompanyId("d1", EMPRESA)).thenReturn(Optional.of(dept));
        when(users.findById("autor-1")).thenReturn(Optional.of(enDepartamento("autor-1", "Ana", "d1")));

        var vista = service.postToDepartment(principal("autor-1", Set.of()), "d1", "Hola equipo");

        assertThat(vista.body()).isEqualTo("Hola equipo");
        verify(messages).save(any());
    }

    @Test
    void elJefeAccedeAlChatAunSinPertenecerAlDepartamento() {
        var dept = departamento("d1", "Soporte", "jefe-1");
        when(departments.findByIdAndCompanyId("d1", EMPRESA)).thenReturn(Optional.of(dept));
        // El jefe no tiene departmentId = d1, pero lidera d1.
        when(users.findById("jefe-1")).thenReturn(Optional.of(enDepartamento("jefe-1", "Jefa", null)));

        var vista = service.postToDepartment(principal("jefe-1", Set.of()), "d1", "Buen trabajo");

        assertThat(vista.body()).isEqualTo("Buen trabajo");
        verify(messages).save(any());
    }

    @Test
    void unExtranoNoAccedeAlChatDeOtroDepartamento() {
        var dept = departamento("d1", "Soporte", "jefe-1");
        when(departments.findByIdAndCompanyId("d1", EMPRESA)).thenReturn(Optional.of(dept));
        when(users.findById("intruso"))
                .thenReturn(Optional.of(enDepartamento("intruso", "Otro", "d2")));

        assertThatThrownBy(() ->
                service.postToDepartment(principal("intruso", Set.of()), "d1", "Cotilleo"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("perteneces");

        verify(messages, never()).save(any());
    }

    @Test
    void soloAparecenLosDepartamentosAccesibles() {
        when(departments.findByCompanyId(EMPRESA)).thenReturn(List.of(
                departamento("d1", "Soporte", "jefe-1"),   // el propio del usuario
                departamento("d2", "Ventas", "u9"),         // liderado por el usuario
                departamento("d3", "Legal", "otro")));      // ajeno
        when(users.findById("u9")).thenReturn(Optional.of(enDepartamento("u9", "Nico", "d1")));

        var accesibles = service.accessibleDepartments(principal("u9", Set.of()));

        assertThat(accesibles).extracting(Department::getId).containsExactlyInAnyOrder("d1", "d2");
    }

    // ------------------------------------------------------------------ apoyo

    private AppPrincipal principal(String id, Set<Permission> permisos) {
        return new AppPrincipal(id, id + "@test.local", UserType.COMPANY_MEMBER, EMPRESA, false, permisos);
    }

    private User miembro(String id, String nombre) {
        var u = new User();
        u.setId(id);
        u.setType(UserType.COMPANY_MEMBER);
        u.setCompanyId(EMPRESA);
        u.setFirstName(nombre);
        return u;
    }

    private User enDepartamento(String id, String nombre, String deptId) {
        var u = miembro(id, nombre);
        u.setDepartmentId(deptId);
        return u;
    }

    private Department departamento(String id, String nombre, String jefeId) {
        var d = new Department();
        d.setId(id);
        d.setCompanyId(EMPRESA);
        d.setName(nombre);
        d.setLeaderUserId(jefeId);
        return d;
    }
}
