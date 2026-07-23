import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { CompanyOrdersService } from '../../core/company-orders.service';
import { formatDate, formatPrice } from '../../core/format';
import { CompanyOrderRow, CompanyOrderStats, StatusOption } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-company-orders',
  imports: [FormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-orders.component.html',
  styleUrl: './company-orders.component.scss',
})
export class CompanyOrdersComponent {
  private readonly api = inject(CompanyOrdersService);

  protected readonly rows = signal<CompanyOrderRow[]>([]);
  protected readonly stats = signal<CompanyOrderStats | null>(null);
  protected readonly statuses = signal<StatusOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Por defecto se ordena por prioridad: lo vencido primero. */
  protected sort = 'priority';
  protected status = '';
  protected onlyOverdue = false;

  protected readonly price = formatPrice;
  protected readonly date = formatDate;

  constructor() {
    this.api.statuses().subscribe({
      next: (s) => this.statuses.set(s),
      error: () => this.statuses.set([]),
    });
    this.api.stats().subscribe({
      next: (s) => this.stats.set(s),
      error: () => this.stats.set(null),
    });
    this.load();
  }

  protected applyFilters(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api
      .list({
        sort: this.sort,
        status: this.status || undefined,
        onlyOverdue: this.onlyOverdue || undefined,
        size: 50,
      })
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err, 'No pudimos cargar los pedidos.'));
          this.loading.set(false);
        },
      });
  }

  /** Etiqueta legible de la urgencia calculada. */
  protected priorityLabel(row: CompanyOrderRow): string {
    switch (row.priority) {
      case 'VENCIDO':
        return 'Vencido';
      case 'POR_VENCER':
        return 'Por vencer';
      case 'CERRADO':
        return 'Cerrado';
      default:
        return 'En plazo';
    }
  }
}
