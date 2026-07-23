package com.ecommerce.kpi;

import com.ecommerce.activity.ActivityService;
import com.ecommerce.common.ApiException;
import com.ecommerce.company.Department;
import com.ecommerce.company.DepartmentRepository;
import com.ecommerce.kpi.dto.KpiDtos.GoalRequest;
import com.ecommerce.kpi.dto.KpiDtos.GoalView;
import com.ecommerce.kpi.dto.KpiDtos.TargetRef;
import com.ecommerce.kpi.dto.KpiDtos.TargetScope;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.Permission;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Metas KPI: crearlas, listarlas con su progreso y borrarlas.
 *
 * <p>Root gestiona cualquier meta de su empresa. Un jefe solo puede gestionar las de su departamento
 * y las de las personas de ese departamento: la especificacion dice que "un jefe de departamento
 * podra asignar, modificar, eliminar metas KPI de los usuarios de su departamento o equipo".
 */
@Service
public class KpiGoalService {

    private final KpiGoalRepository goals;
    private final KpiCalculator calculator;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final ActivityService activityService;

    public KpiGoalService(KpiGoalRepository goals, KpiCalculator calculator, UserRepository users,
                          DepartmentRepository departments, ActivityService activityService) {
        this.goals = goals;
        this.calculator = calculator;
        this.users = users;
        this.departments = departments;
        this.activityService = activityService;
    }

    public List<GoalView> list(AppPrincipal actor) {
        String companyId = companyOf(actor);
        boolean verTodo = actor.has(Permission.KPI_VIEW_ALL) || actor.has(Permission.KPI_GOAL_MANAGE);

        var todas = goals.findByCompanyIdOrderByCreatedAtDesc(companyId);
        var visibles = verTodo ? todas : todas.stream().filter(g -> canSee(actor, g)).toList();
        return visibles.stream().map(this::toView).toList();
    }

    public GoalView create(AppPrincipal actor, GoalRequest request) {
        String companyId = companyOf(actor);

        var metric = parseMetric(request.metric());
        var targetType = parseTargetType(request.targetType());

        if (request.periodEnd().isBefore(request.periodStart())) {
            throw ApiException.badRequest("BAD_PERIOD", "El fin del periodo es anterior al inicio.");
        }
        validateScope(metric, targetType);

        var goal = new KpiGoal();
        goal.setCompanyId(companyId);
        goal.setMetric(metric);
        goal.setTargetType(targetType);
        resolveTarget(goal, targetType, request.targetId(), companyId);
        goal.setTarget(request.target());
        goal.setPeriodStart(request.periodStart());
        goal.setPeriodEnd(request.periodEnd());
        goal.setCreatedByUserId(actor.userId());

        // Un jefe solo puede fijar metas dentro de su ambito.
        requireManageable(actor, goal);
        goal = goals.save(goal);

        activityService.record(actor, "KPI_GOAL_CREATE", "KpiGoal", goal.getId(),
                Map.of("metrica", metric.name()));
        return toView(goal);
    }

    public GoalView update(AppPrincipal actor, String goalId, GoalRequest request) {
        var goal = require(actor, goalId);
        requireManageable(actor, goal);

        var metric = parseMetric(request.metric());
        var targetType = parseTargetType(request.targetType());
        validateScope(metric, targetType);
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw ApiException.badRequest("BAD_PERIOD", "El fin del periodo es anterior al inicio.");
        }

        goal.setMetric(metric);
        goal.setTargetType(targetType);
        resolveTarget(goal, targetType, request.targetId(), goal.getCompanyId());
        goal.setTarget(request.target());
        goal.setPeriodStart(request.periodStart());
        goal.setPeriodEnd(request.periodEnd());
        goal.setUpdatedAt(Instant.now());

        requireManageable(actor, goal);
        goals.save(goal);

        activityService.record(actor, "KPI_GOAL_UPDATE", "KpiGoal", goal.getId(), Map.of());
        return toView(goal);
    }

    public void delete(AppPrincipal actor, String goalId) {
        var goal = require(actor, goalId);
        requireManageable(actor, goal);
        goals.delete(goal);
        activityService.record(actor, "KPI_GOAL_DELETE", "KpiGoal", goalId, Map.of());
    }

    /**
     * Destinos que el actor puede fijar como objetivo. Root: todos los departamentos y usuarios de su
     * empresa. Un jefe: solo los departamentos que lidera y las personas de esos departamentos.
     */
    public TargetScope targets(AppPrincipal actor) {
        String companyId = companyOf(actor);
        var deps = departments.findByCompanyId(companyId);
        var miembros = users.findByCompanyId(companyId);

        if (actor.root()) {
            return new TargetScope(
                    deps.stream().map(d -> new TargetRef(d.getId(), d.getName(), null)).toList(),
                    miembros.stream()
                            .map(u -> new TargetRef(u.getId(), u.displayName().trim(),
                                    u.getDepartmentId()))
                            .toList());
        }

        var misDepartamentos = ledDepartments(actor);
        var depsVisibles = deps.stream()
                .filter(d -> misDepartamentos.contains(d.getId()))
                .map(d -> new TargetRef(d.getId(), d.getName(), null))
                .toList();
        var usuariosVisibles = miembros.stream()
                .filter(u -> u.getDepartmentId() != null && misDepartamentos.contains(u.getDepartmentId()))
                .map(u -> new TargetRef(u.getId(), u.displayName().trim(), u.getDepartmentId()))
                .toList();
        return new TargetScope(depsVisibles, usuariosVisibles);
    }

    // ------------------------------------------------------------------ ambito del jefe

    /**
     * Comprueba que quien actua puede gestionar la meta. Root (con KPI_GOAL_MANAGE) puede con todas;
     * un jefe solo con las de su departamento o las de las personas de su departamento.
     */
    private void requireManageable(AppPrincipal actor, KpiGoal goal) {
        if (actor.root()) {
            return;
        }
        if (!actor.has(Permission.KPI_GOAL_MANAGE)) {
            throw ApiException.forbidden("NO_KPI_PERMISSION",
                    "No tienes permiso para gestionar metas KPI.");
        }
        if (!inLeaderScope(actor, goal)) {
            throw ApiException.forbidden("OUT_OF_SCOPE",
                    "Solo puedes gestionar metas de tu departamento o de tu equipo.");
        }
    }

    /** Departamentos que lidera quien actua. */
    private List<String> ledDepartments(AppPrincipal actor) {
        return departments.findByCompanyId(actor.companyId()).stream()
                .filter(d -> actor.userId().equals(d.getLeaderUserId()))
                .map(Department::getId)
                .toList();
    }

    private boolean inLeaderScope(AppPrincipal actor, KpiGoal goal) {
        var misDepartamentos = ledDepartments(actor);
        if (misDepartamentos.isEmpty()) {
            return false;
        }
        return switch (goal.getTargetType()) {
            case DEPARTMENT -> misDepartamentos.contains(goal.getTargetId());
            case USER -> users.findById(goal.getTargetId())
                    .map(User::getDepartmentId)
                    .map(misDepartamentos::contains)
                    .orElse(false);
            // Una meta de toda la empresa solo la fija root.
            case COMPANY -> false;
        };
    }

    private boolean canSee(AppPrincipal actor, KpiGoal goal) {
        // Ve sus propias metas y las de los departamentos que lidera.
        if (goal.getTargetType() == KpiGoal.TargetType.USER
                && actor.userId().equals(goal.getTargetId())) {
            return true;
        }
        return inLeaderScope(actor, goal);
    }

    // ------------------------------------------------------------------ apoyo

    private String companyOf(AppPrincipal actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NOT_A_COMPANY_MEMBER",
                    "Esta seccion es exclusiva de las cuentas de empresa.");
        }
        return actor.companyId();
    }

    private KpiGoal require(AppPrincipal actor, String goalId) {
        return goals.findByIdAndCompanyId(goalId, companyOf(actor))
                .orElseThrow(() -> ApiException.notFound("La meta no existe en tu empresa."));
    }

    private void validateScope(KpiMetric metric, KpiGoal.TargetType targetType) {
        if (metric.getScope() == KpiMetric.Scope.COMPANY_OR_DEPARTMENT
                && targetType == KpiGoal.TargetType.USER) {
            throw ApiException.badRequest("BAD_SCOPE",
                    "La metrica \"%s\" no se puede asignar a una persona.".formatted(metric.getLabel()));
        }
    }

    private void resolveTarget(KpiGoal goal, KpiGoal.TargetType type, String targetId,
                               String companyId) {
        switch (type) {
            case COMPANY -> {
                goal.setTargetId(null);
                goal.setTargetName("Toda la empresa");
            }
            case DEPARTMENT -> {
                var dept = departments.findByIdAndCompanyId(targetId, companyId)
                        .orElseThrow(() -> ApiException.badRequest("DEPT_NOT_FOUND",
                                "El departamento no existe en tu empresa."));
                goal.setTargetId(dept.getId());
                goal.setTargetName(dept.getName());
            }
            case USER -> {
                var user = users.findById(targetId)
                        .filter(u -> companyId.equals(u.getCompanyId()))
                        .orElseThrow(() -> ApiException.badRequest("USER_NOT_FOUND",
                                "El usuario no existe en tu empresa."));
                goal.setTargetId(user.getId());
                goal.setTargetName(user.displayName().trim());
            }
        }
    }

    /** Usuarios a los que se atribuye el valor real de la meta segun su ambito. */
    private List<String> scopeUsers(KpiGoal goal) {
        return switch (goal.getTargetType()) {
            case COMPANY -> List.of();
            case USER -> List.of(goal.getTargetId());
            case DEPARTMENT -> users.findByCompanyId(goal.getCompanyId()).stream()
                    .filter(u -> goal.getTargetId().equals(u.getDepartmentId()))
                    .map(User::getId)
                    .toList();
        };
    }

    private GoalView toView(KpiGoal goal) {
        var actual = calculator.actual(goal.getCompanyId(), goal.getMetric(),
                goal.getPeriodStart(), goal.getPeriodEnd(), scopeUsers(goal));

        double progress = goal.getTarget().compareTo(BigDecimal.ZERO) == 0 ? 0
                : actual.multiply(BigDecimal.valueOf(100))
                        .divide(goal.getTarget(), 1, RoundingMode.HALF_UP).doubleValue();

        return new GoalView(goal.getId(), goal.getMetric().name(), goal.getMetric().getLabel(),
                goal.getMetric().getUnit().name(), goal.getTargetType().name(), goal.getTargetId(),
                goal.getTargetName(), goal.getTarget(), actual, progress,
                actual.compareTo(goal.getTarget()) >= 0, goal.getPeriodStart(), goal.getPeriodEnd());
    }

    private KpiMetric parseMetric(String value) {
        try {
            return KpiMetric.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("INVALID_METRIC", "Esa metrica no existe.");
        }
    }

    private KpiGoal.TargetType parseTargetType(String value) {
        try {
            return KpiGoal.TargetType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("INVALID_TARGET", "Ese tipo de asignacion no existe.");
        }
    }
}
