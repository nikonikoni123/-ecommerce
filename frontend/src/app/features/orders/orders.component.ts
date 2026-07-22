import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { OrderService } from '../../core/cart.service';
import { formatDate, formatPrice } from '../../core/format';
import { OrderSummary } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

type Scope = 'active' | 'history';

@Component({
  selector: 'app-orders',
  imports: [RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.scss',
})
export class OrdersComponent {
  private readonly api = inject(OrderService);

  protected readonly scope = signal<Scope>('active');
  protected readonly orders = signal<OrderSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly price = formatPrice;
  protected readonly date = formatDate;

  constructor() {
    this.load();
  }

  protected select(scope: Scope): void {
    if (this.scope() === scope) {
      return;
    }
    this.scope.set(scope);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.list(this.scope(), 0, 50).subscribe({
      next: (page) => {
        this.orders.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar tus pedidos.'));
        this.loading.set(false);
      },
    });
  }
}
