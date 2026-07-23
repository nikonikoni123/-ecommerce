import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import { CaseRow } from '../../core/models';
import { SupportService } from '../../core/support.service';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-my-cases',
  imports: [RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-cases.component.html',
  styleUrl: './my-cases.component.scss',
})
export class MyCasesComponent {
  private readonly api = inject(SupportService);

  protected readonly cases = signal<CaseRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly date = formatDate;

  constructor() {
    this.api.myCases(0, 50).subscribe({
      next: (page) => {
        this.cases.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar tus casos.'));
        this.loading.set(false);
      },
    });
  }
}
