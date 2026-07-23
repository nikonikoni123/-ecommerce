import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import { HasPermissionDirective } from '../../core/has-permission.directive';
import { CaseDetail, CaseStatusOption, Permission } from '../../core/models';
import { CompanySupportService } from '../../core/support.service';
import { priorityClass, priorityLabel, sentimentLabel } from '../../shared/case-badges';
import { AlertComponent } from '../../shared/alert.component';
import { CaseThreadComponent } from '../../shared/case-thread.component';

@Component({
  selector: 'app-company-case-detail',
  imports: [FormsModule, RouterLink, AlertComponent, CaseThreadComponent, HasPermissionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-case-detail.component.html',
  styleUrl: './company-case-detail.component.scss',
})
export class CompanyCaseDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(CompanySupportService);

  protected readonly Permission = Permission;

  protected readonly case = signal<CaseDetail | null>(null);
  protected readonly statuses = signal<CaseStatusOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  protected reply = '';

  protected readonly date = formatDate;
  protected readonly priorityLabel = priorityLabel;
  protected readonly priorityClass = priorityClass;
  protected readonly sentimentLabel = sentimentLabel;

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  constructor() {
    this.api.statuses().subscribe({
      next: (s) => this.statuses.set(s),
      error: () => this.statuses.set([]),
    });
    this.load();
  }

  private load(): void {
    this.api.detail(this.id).subscribe({
      next: (c) => {
        this.case.set(c);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar el caso.'));
        this.loading.set(false);
      },
    });
  }

  /** Solo se ofrecen las transiciones que el backend aceptaria desde el estado actual. */
  protected availableTransitions(): CaseStatusOption[] {
    const actual = this.case()?.status;
    const def = this.statuses().find((s) => s.status === actual);
    if (!def) {
      return [];
    }
    return this.statuses().filter((s) => def.allowedTransitions.includes(s.status));
  }

  protected assignToMe(): void {
    this.run(this.api.assign(this.id), 'Caso asignado a ti.');
  }

  protected send(): void {
    if (!this.reply.trim() || this.busy()) {
      return;
    }
    this.run(this.api.reply(this.id, this.reply.trim()), 'Respuesta enviada. El cliente fue avisado.');
    this.reply = '';
  }

  protected changeStatus(target: string): void {
    const label = this.statuses().find((s) => s.status === target)?.label ?? target;
    this.run(this.api.changeStatus(this.id, target), `Caso marcado como "${label}".`);
  }

  private run(source: ReturnType<CompanySupportService['detail']>, ok: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);

    source.subscribe({
      next: (c) => {
        this.case.set(c);
        this.busy.set(false);
        this.message.set(ok);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(errorMessage(err, 'No pudimos aplicar el cambio.'));
      },
    });
  }
}
