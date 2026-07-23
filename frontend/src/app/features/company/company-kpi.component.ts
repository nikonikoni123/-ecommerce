import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/api-error';
import { formatDate, formatPrice } from '../../core/format';
import { AuthService } from '../../core/auth.service';
import {
  Dashboard,
  GoalRequest,
  GoalView,
  MetricOption,
  Permission,
  TargetScope,
} from '../../core/models';
import { KpiService } from '../../core/admin.service';
import { AlertComponent } from '../../shared/alert.component';
import {
  BarChartComponent,
  DonutChartComponent,
  GaugeComponent,
  LineChartComponent,
} from '../../shared/charts.component';

/** Borrador del formulario de metas. */
interface GoalDraft {
  id: string | null;
  metric: string;
  targetType: string;
  targetId: string;
  target: number | null;
  periodStart: string;
  periodEnd: string;
}

@Component({
  selector: 'app-company-kpi',
  imports: [
    FormsModule,
    AlertComponent,
    BarChartComponent,
    DonutChartComponent,
    GaugeComponent,
    LineChartComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-kpi.component.html',
  styleUrl: './company-kpi.component.scss',
})
export class CompanyKpiComponent {
  private readonly api = inject(KpiService);
  private readonly auth = inject(AuthService);

  protected readonly dashboard = signal<Dashboard | null>(null);
  protected readonly goals = signal<GoalView[]>([]);
  protected readonly metrics = signal<MetricOption[]>([]);
  protected readonly scope = signal<TargetScope>({ departments: [], users: [] });

  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly loadingDash = signal(true);

  protected months = 6;
  protected readonly draft = signal<GoalDraft>(this.emptyDraft());

  protected readonly price = formatPrice;
  protected readonly date = formatDate;

  protected readonly canViewDashboard = computed(
    () => this.auth.has(Permission.KPI_VIEW_ALL) || this.auth.has(Permission.KPI_VIEW_TEAM),
  );
  protected readonly canManage = computed(() => this.auth.has(Permission.KPI_GOAL_MANAGE));
  protected readonly editingGoal = computed(() => this.draft().id !== null);

  /** La metrica elegida y si acepta objetivo por persona (segun su alcance). */
  protected readonly selectedMetric = computed(() =>
    this.metrics().find((m) => m.metric === this.draft().metric),
  );
  protected readonly allowsUser = computed(
    () => this.selectedMetric()?.scope !== 'COMPANY_OR_DEPARTMENT',
  );

  constructor() {
    if (this.canViewDashboard()) {
      this.loadDashboard();
    } else {
      this.loadingDash.set(false);
    }
    this.reloadGoals();
    if (this.canManage()) {
      this.api.metrics().subscribe({ next: (m) => this.metrics.set(m), error: () => undefined });
      this.api.targets().subscribe({ next: (t) => this.scope.set(t), error: () => undefined });
    }
  }

  protected loadDashboard(): void {
    this.loadingDash.set(true);
    this.api.dashboard(this.months).subscribe({
      next: (d) => {
        this.dashboard.set(d);
        this.loadingDash.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar el panel.'));
        this.loadingDash.set(false);
      },
    });
  }

  private reloadGoals(): void {
    this.api.goals().subscribe({ next: (g) => this.goals.set(g), error: () => undefined });
  }

  // ------------------------------------------------------------------ metas
  protected editGoal(goal: GoalView): void {
    this.draft.set({
      id: goal.id,
      metric: goal.metric,
      targetType: goal.targetType,
      targetId: goal.targetId ?? '',
      target: goal.target,
      periodStart: goal.periodStart.slice(0, 10),
      periodEnd: goal.periodEnd.slice(0, 10),
    });
    this.notice.set(null);
  }

  protected newGoal(): void {
    this.draft.set(this.emptyDraft());
    this.notice.set(null);
  }

  protected patchDraft(patch: Partial<GoalDraft>): void {
    this.draft.update((d) => ({ ...d, ...patch }));
    // Si la metrica pasa a ser de ambito empresa/departamento y habia un usuario elegido, se limpia.
    if (patch.metric && !this.allowsUser() && this.draft().targetType === 'USER') {
      this.draft.update((d) => ({ ...d, targetType: 'DEPARTMENT', targetId: '' }));
    }
    if (patch.targetType) {
      this.draft.update((d) => ({ ...d, targetId: '' }));
    }
  }

  protected saveGoal(): void {
    const d = this.draft();
    if (!d.metric || !d.target || !d.periodStart || !d.periodEnd) {
      this.error.set('Completa metrica, objetivo y periodo.');
      return;
    }
    if (d.targetType !== 'COMPANY' && !d.targetId) {
      this.error.set('Elige el departamento o la persona de la meta.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    const payload: GoalRequest = {
      metric: d.metric,
      targetType: d.targetType,
      targetId: d.targetType === 'COMPANY' ? null : d.targetId,
      target: d.target,
      periodStart: new Date(d.periodStart).toISOString(),
      periodEnd: new Date(d.periodEnd + 'T23:59:59').toISOString(),
    };
    const req = d.id ? this.api.updateGoal(d.id, payload) : this.api.createGoal(payload);
    req.subscribe({
      next: () => {
        this.notice.set(d.id ? 'Meta actualizada.' : 'Meta creada.');
        this.draft.set(this.emptyDraft());
        this.reloadGoals();
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos guardar la meta.'));
        this.busy.set(false);
      },
    });
  }

  protected deleteGoal(goal: GoalView): void {
    if (!confirm(`Eliminar la meta de ${goal.metricLabel} para ${goal.targetName}?`)) {
      return;
    }
    this.api.deleteGoal(goal.id).subscribe({
      next: () => {
        this.notice.set('Meta eliminada.');
        this.reloadGoals();
      },
      error: (err) => this.error.set(errorMessage(err, 'No pudimos eliminar la meta.')),
    });
  }

  protected metricValue(value: number, unit: string): string {
    return unit === 'CURRENCY'
      ? formatPrice(value)
      : new Intl.NumberFormat('es-CO').format(value);
  }

  private emptyDraft(): GoalDraft {
    const today = new Date().toISOString().slice(0, 10);
    const monthAgo = new Date(Date.now() - 30 * 864e5).toISOString().slice(0, 10);
    return {
      id: null,
      metric: '',
      targetType: 'COMPANY',
      targetId: '',
      target: null,
      periodStart: monthAgo,
      periodEnd: today,
    };
  }
}
