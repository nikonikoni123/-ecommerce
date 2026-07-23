package com.ecommerce.kpi;

import com.ecommerce.auth.dto.AuthDtos.MessageResponse;
import com.ecommerce.kpi.dto.KpiDtos.Dashboard;
import com.ecommerce.kpi.dto.KpiDtos.GoalRequest;
import com.ecommerce.kpi.dto.KpiDtos.GoalView;
import com.ecommerce.kpi.dto.KpiDtos.MetricOption;
import com.ecommerce.kpi.dto.KpiDtos.TargetScope;
import com.ecommerce.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metas KPI y panel de resultados.
 *
 * <p>Ver el panel exige poder ver KPI (propios, de equipo o de toda la empresa); gestionar metas
 * exige {@code KPI_GOAL_MANAGE}, y ademas el servicio limita a los jefes a su propio ambito.
 */
@RestController
@RequestMapping("/api/company/kpi")
@Tag(name = "KPI", description = "Metas y panel de resultados de la empresa")
public class KpiController {

    private final KpiGoalService goalService;
    private final DashboardService dashboardService;

    public KpiController(KpiGoalService goalService, DashboardService dashboardService) {
        this.goalService = goalService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyAuthority('KPI_VIEW_OWN','KPI_VIEW_TEAM','KPI_VIEW_ALL','KPI_GOAL_MANAGE')")
    @Operation(summary = "Metricas disponibles para fijar metas")
    public List<MetricOption> metrics() {
        return Arrays.stream(KpiMetric.values())
                .map(m -> new MetricOption(m.name(), m.getLabel(), m.getUnit().name(),
                        m.getScope().name()))
                .toList();
    }

    @GetMapping("/goals")
    @PreAuthorize("hasAnyAuthority('KPI_VIEW_OWN','KPI_VIEW_TEAM','KPI_VIEW_ALL','KPI_GOAL_MANAGE')")
    @Operation(summary = "Metas visibles para quien consulta, con su progreso calculado")
    public List<GoalView> goals(@AuthenticationPrincipal AppPrincipal actor) {
        return goalService.list(actor);
    }

    @GetMapping("/targets")
    @PreAuthorize("hasAuthority('KPI_GOAL_MANAGE')")
    @Operation(summary = "Departamentos y usuarios que puedo fijar como objetivo de una meta")
    public TargetScope targets(@AuthenticationPrincipal AppPrincipal actor) {
        return goalService.targets(actor);
    }

    @PostMapping("/goals")
    @PreAuthorize("hasAuthority('KPI_GOAL_MANAGE')")
    @Operation(summary = "Crear una meta KPI")
    public GoalView createGoal(@AuthenticationPrincipal AppPrincipal actor,
                               @Valid @RequestBody GoalRequest request) {
        return goalService.create(actor, request);
    }

    @PutMapping("/goals/{id}")
    @PreAuthorize("hasAuthority('KPI_GOAL_MANAGE')")
    @Operation(summary = "Modificar una meta KPI")
    public GoalView updateGoal(@AuthenticationPrincipal AppPrincipal actor, @PathVariable String id,
                               @Valid @RequestBody GoalRequest request) {
        return goalService.update(actor, id, request);
    }

    @DeleteMapping("/goals/{id}")
    @PreAuthorize("hasAuthority('KPI_GOAL_MANAGE')")
    @Operation(summary = "Eliminar una meta KPI")
    public MessageResponse deleteGoal(@AuthenticationPrincipal AppPrincipal actor,
                                      @PathVariable String id) {
        goalService.delete(actor, id);
        return new MessageResponse("Meta eliminada.");
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('KPI_VIEW_ALL','KPI_VIEW_TEAM')")
    @Operation(summary = "Panel de resultados con las series de gestion")
    public Dashboard dashboard(@AuthenticationPrincipal AppPrincipal actor,
                               @RequestParam(defaultValue = "6") int months) {
        return dashboardService.build(actor, Math.min(Math.max(months, 1), 12));
    }
}
