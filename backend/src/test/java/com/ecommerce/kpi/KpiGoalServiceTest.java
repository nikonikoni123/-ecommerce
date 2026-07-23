package com.ecommerce.kpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.activity.ActivityService;
import com.ecommerce.common.ApiException;
import com.ecommerce.company.Department;
import com.ecommerce.company.DepartmentRepository;
import com.ecommerce.kpi.dto.KpiDtos.GoalRequest;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.Permission;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las metas KPI concentran dos reglas delicadas de la especificacion: el progreso se calcula desde
 * los datos reales (no se guarda) y un jefe solo puede tocar las metas de su propio departamento o
 * equipo. Se prueban ambas, mas la validacion de ambito de las metricas de ventas.
 */
@ExtendWith(MockitoExtension.class)
class KpiGoalServiceTest {

    private static final String EMPRESA = "company-1";

    @Mock private KpiGoalRepository goals;
    @Mock private KpiCalculator calculator;
    @Mock private UserRepository users;
    @Mock private DepartmentRepository departments;
    @Mock private ActivityService activityService;

    private KpiGoalService service;

    @BeforeEach
    void setUp() {
        service = new KpiGoalService(goals, calculator, users, departments, activityService);
        lenient().when(goals.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------ progreso calculado

    @Test
    void elProgresoSeCalculaDesdeElValorReal() {
        // 30 de un objetivo de 40 => 75%, aun sin alcanzar.
        when(calculator.actual(eq(EMPRESA), eq(KpiMetric.CASOS_RESUELTOS), any(), any(), anyList()))
                .thenReturn(new BigDecimal("30"));
        when(users.findById("u2"))
                .thenReturn(Optional.of(miembro("u2", "Beto", "d1")));

        var vista = service.create(root(), request("CASOS_RESUELTOS", "USER", "u2", "40"));

        assertThat(vista.actual()).isEqualByComparingTo("30");
        assertThat(vista.progress()).isEqualTo(75.0);
        assertThat(vista.achieved()).isFalse();
    }

    @Test
    void unaMetaAlcanzadaSeMarcaComoLograda() {
        when(calculator.actual(eq(EMPRESA), eq(KpiMetric.CASOS_RESUELTOS), any(), any(), anyList()))
                .thenReturn(new BigDecimal("50"));
        when(users.findById("u2")).thenReturn(Optional.of(miembro("u2", "Beto", "d1")));

        var vista = service.create(root(), request("CASOS_RESUELTOS", "USER", "u2", "40"));

        assertThat(vista.progress()).isEqualTo(125.0);
        assertThat(vista.achieved()).isTrue();
    }

    // ------------------------------------------------------------------ validacion de ambito

    @Test
    void lasVentasNoSePuedenAsignarAUnaPersona() {
        assertThatThrownBy(() -> service.create(root(), request("VENTAS", "USER", "u2", "1000")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no se puede asignar a una persona");

        verify(goals, never()).save(any());
    }

    // ------------------------------------------------------------------ ambito del jefe

    @Test
    void unJefeCreaMetasEnElDepartamentoQueLidera() {
        var jefe = jefe("jefe-1", Set.of(Permission.KPI_GOAL_MANAGE));
        when(departments.findByCompanyId(EMPRESA))
                .thenReturn(List.of(departamento("d1", "Soporte", "jefe-1")));
        when(departments.findByIdAndCompanyId("d1", EMPRESA))
                .thenReturn(Optional.of(departamento("d1", "Soporte", "jefe-1")));
        when(users.findByCompanyId(EMPRESA)).thenReturn(List.of(miembro("u2", "Beto", "d1")));
        when(calculator.actual(any(), any(), any(), any(), anyList())).thenReturn(BigDecimal.TEN);

        var vista = service.create(jefe, request("CASOS_RESUELTOS", "DEPARTMENT", "d1", "100"));

        assertThat(vista.targetName()).isEqualTo("Soporte");
        verify(goals).save(any());
    }

    @Test
    void unJefeNoPuedeCrearMetasDeOtroDepartamento() {
        var jefe = jefe("jefe-1", Set.of(Permission.KPI_GOAL_MANAGE));
        // Lidera d1, pero intenta fijar meta en d2.
        when(departments.findByCompanyId(EMPRESA))
                .thenReturn(List.of(departamento("d1", "Soporte", "jefe-1")));
        when(departments.findByIdAndCompanyId("d2", EMPRESA))
                .thenReturn(Optional.of(departamento("d2", "Ventas", "otro")));

        assertThatThrownBy(() ->
                service.create(jefe, request("CASOS_RESUELTOS", "DEPARTMENT", "d2", "100")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tu departamento");

        verify(goals, never()).save(any());
    }

    @Test
    void unJefeNoPuedeCrearMetasDeTodaLaEmpresa() {
        var jefe = jefe("jefe-1", Set.of(Permission.KPI_GOAL_MANAGE));
        when(departments.findByCompanyId(EMPRESA))
                .thenReturn(List.of(departamento("d1", "Soporte", "jefe-1")));

        assertThatThrownBy(() -> service.create(jefe, request("VENTAS", "COMPANY", null, "5000")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tu departamento");

        verify(goals, never()).save(any());
    }

    // ------------------------------------------------------------------ apoyo

    private GoalRequest request(String metric, String targetType, String targetId, String target) {
        var now = Instant.now();
        return new GoalRequest(metric, targetType, targetId, new BigDecimal(target),
                now.minus(30, ChronoUnit.DAYS), now);
    }

    private AppPrincipal root() {
        return new AppPrincipal("root-1", "root@test.local", UserType.COMPANY_MEMBER, EMPRESA, true,
                Set.of());
    }

    private AppPrincipal jefe(String id, Set<Permission> permisos) {
        return new AppPrincipal(id, id + "@test.local", UserType.COMPANY_MEMBER, EMPRESA, false,
                permisos);
    }

    private Department departamento(String id, String nombre, String jefeId) {
        var d = new Department();
        d.setId(id);
        d.setCompanyId(EMPRESA);
        d.setName(nombre);
        d.setLeaderUserId(jefeId);
        return d;
    }

    private User miembro(String id, String nombre, String deptId) {
        var u = new User();
        u.setId(id);
        u.setType(UserType.COMPANY_MEMBER);
        u.setCompanyId(EMPRESA);
        u.setFirstName(nombre);
        u.setDepartmentId(deptId);
        return u;
    }
}
