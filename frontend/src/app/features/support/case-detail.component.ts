import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import { CaseDetail } from '../../core/models';
import { SupportService } from '../../core/support.service';
import { AlertComponent } from '../../shared/alert.component';
import { CaseThreadComponent } from '../../shared/case-thread.component';

/** Detalle de un caso desde el lado del cliente: ve el hilo y responde. */
@Component({
  selector: 'app-case-detail',
  imports: [FormsModule, RouterLink, AlertComponent, CaseThreadComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './case-detail.component.html',
  styleUrl: './case-detail.component.scss',
})
export class CaseDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(SupportService);

  protected readonly case = signal<CaseDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected reply = '';
  protected readonly date = formatDate;

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  constructor() {
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

  protected send(): void {
    if (!this.reply.trim() || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);

    this.api.reply(this.id, this.reply.trim()).subscribe({
      next: (c) => {
        this.case.set(c);
        this.reply = '';
        this.sending.set(false);
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(errorMessage(err, 'No pudimos enviar tu mensaje.'));
      },
    });
  }
}
