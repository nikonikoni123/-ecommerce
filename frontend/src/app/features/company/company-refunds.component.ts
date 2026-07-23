import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { CompanyOrdersService } from '../../core/company-orders.service';
import { formatDate, formatPrice } from '../../core/format';
import { RefundView } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-company-refunds',
  imports: [FormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-refunds.component.html',
  styleUrl: './company-refunds.component.scss',
})
export class CompanyRefundsComponent {
  private readonly api = inject(CompanyOrdersService);

  protected readonly refunds = signal<RefundView[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  /** Solicitud cuya caja de respuesta esta abierta. */
  protected readonly resolving = signal<string | null>(null);
  protected resolution = '';

  protected status = 'PENDIENTE';

  protected readonly price = formatPrice;
  protected readonly date = formatDate;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.refunds(this.status || undefined).subscribe({
      next: (page) => {
        this.refunds.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar las solicitudes.'));
        this.loading.set(false);
      },
    });
  }

  protected open(id: string): void {
    this.resolving.set(this.resolving() === id ? null : id);
    this.resolution = '';
  }

  protected resolve(id: string, approve: boolean): void {
    if (this.busy()) {
      return;
    }
    const texto = approve
      ? 'Aprobar el reembolso? El pedido pasara a REEMBOLSADO y el stock volvera al catalogo.'
      : 'Rechazar la solicitud? Se avisara al cliente.';
    if (!confirm(texto)) {
      return;
    }

    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);

    this.api.resolveRefund(id, approve, this.resolution || undefined).subscribe({
      next: () => {
        this.busy.set(false);
        this.resolving.set(null);
        this.resolution = '';
        this.message.set(approve
          ? 'Reembolso aprobado. El cliente fue avisado y el stock se repuso.'
          : 'Solicitud rechazada. El cliente fue avisado.');
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(errorMessage(err, 'No pudimos resolver la solicitud.'));
      },
    });
  }
}
