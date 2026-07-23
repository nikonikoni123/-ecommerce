import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import { CaseRow, CaseStats, CaseStatusOption } from '../../core/models';
import { CompanySupportService } from '../../core/support.service';
import { priorityClass, priorityLabel } from '../../shared/case-badges';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-company-cases',
  imports: [FormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-cases.component.html',
  styleUrl: './company-cases.component.scss',
})
export class CompanyCasesComponent {
  private readonly api = inject(CompanySupportService);

  protected readonly rows = signal<CaseRow[]>([]);
  protected readonly stats = signal<CaseStats | null>(null);
  protected readonly statuses = signal<CaseStatusOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected sort = 'priority';
  protected status = '';

  protected readonly date = formatDate;
  protected readonly priorityLabel = priorityLabel;
  protected readonly priorityClass = priorityClass;

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

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.inbox(this.status || undefined, this.sort, 0, 50).subscribe({
      next: (page) => {
        this.rows.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar los casos.'));
        this.loading.set(false);
      },
    });
  }
}
