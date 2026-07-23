import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import { ActivityRow, MemberView } from '../../core/models';
import { CompanyAdminService } from '../../core/admin.service';
import { AlertComponent } from '../../shared/alert.component';

/** Etiquetas legibles para los codigos de accion del registro de actividad. */
const ACTION_LABELS: Record<string, string> = {
  ROLE_CREATE: 'Creo un cargo',
  ROLE_UPDATE: 'Modifico un cargo',
  ROLE_DELETE: 'Elimino un cargo',
  USER_CREATE: 'Creo un usuario',
  USER_UPDATE: 'Modifico un usuario',
  USER_DELETE: 'Elimino un usuario',
  DEPARTMENT_CREATE: 'Creo un departamento',
  DEPARTMENT_UPDATE: 'Modifico un departamento',
  DEPARTMENT_DELETE: 'Elimino un departamento',
  KPI_GOAL_CREATE: 'Creo una meta KPI',
  KPI_GOAL_UPDATE: 'Modifico una meta KPI',
  KPI_GOAL_DELETE: 'Elimino una meta KPI',
  ORDER_STATUS_CHANGE: 'Cambio el estado de un pedido',
  ORDER_ITEMS_CHANGE: 'Cambio productos de un pedido',
  REFUND_APPROVE: 'Aprobo un reembolso',
  REFUND_REJECT: 'Rechazo un reembolso',
  CASE_ASSIGN: 'Asigno un caso',
  CASE_REPLY: 'Respondio un caso',
  CASE_STATUS_CHANGE: 'Cambio el estado de un caso',
};

@Component({
  selector: 'app-company-activity',
  imports: [FormsModule, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-activity.component.html',
  styleUrl: './company-activity.component.scss',
})
export class CompanyActivityComponent {
  private readonly api = inject(CompanyAdminService);

  protected readonly rows = signal<ActivityRow[]>([]);
  protected readonly members = signal<MemberView[]>([]);
  protected readonly loading = signal(true);
  protected readonly last = signal(true);
  protected readonly error = signal<string | null>(null);

  protected userFilter = '';
  private page = 0;

  protected readonly date = formatDate;

  constructor() {
    // El filtro por usuario solo esta disponible si ademas se pueden gestionar usuarios.
    this.api.members().subscribe({
      next: (m) => this.members.set(m),
      error: () => undefined,
    });
    this.load(true);
  }

  protected changeFilter(): void {
    this.load(true);
  }

  protected load(reset: boolean): void {
    if (reset) {
      this.page = 0;
      this.rows.set([]);
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.activity(this.userFilter || undefined, this.page, 40).subscribe({
      next: (res) => {
        this.rows.update((list) => [...list, ...res.content]);
        this.last.set(res.last);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar la actividad.'));
        this.loading.set(false);
      },
    });
  }

  protected more(): void {
    this.page += 1;
    this.load(false);
  }

  protected actionLabel(action: string): string {
    return ACTION_LABELS[action] ?? action;
  }

  protected detail(row: ActivityRow): string {
    const meta = row.metadata ?? {};
    const parts = Object.values(meta);
    if (parts.length > 0) {
      return parts.join(', ');
    }
    return row.targetType ? `${row.targetType} ${row.targetId ?? ''}`.trim() : '—';
  }
}
