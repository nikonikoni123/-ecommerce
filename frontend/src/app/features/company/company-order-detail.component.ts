import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { CompanyOrdersService } from '../../core/company-orders.service';
import { formatDate, formatPrice } from '../../core/format';
import { HasPermissionDirective } from '../../core/has-permission.directive';
import { OrderDetail, Permission, StatusOption } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-company-order-detail',
  imports: [FormsModule, RouterLink, AlertComponent, HasPermissionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-order-detail.component.html',
  styleUrl: './company-order-detail.component.scss',
})
export class CompanyOrderDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(CompanyOrdersService);

  protected readonly Permission = Permission;

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly statuses = signal<StatusOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  /** Cantidades editables, indexadas por producto. Cero elimina la linea. */
  protected readonly quantities = signal<Record<string, number>>({});
  protected readonly editingItems = signal(false);

  protected statusNote = '';
  protected itemsNote = '';

  protected readonly price = formatPrice;
  protected readonly date = formatDate;

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
      next: (o) => {
        this.order.set(o);
        this.resetQuantities(o);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar el pedido.'));
        this.loading.set(false);
      },
    });
  }

  private resetQuantities(o: OrderDetail): void {
    const mapa: Record<string, number> = {};
    o.items.forEach((i) => (mapa[i.productId] = i.quantity));
    this.quantities.set(mapa);
  }

  /** Solo se ofrecen las transiciones que el backend aceptaria. */
  protected availableTransitions(): StatusOption[] {
    const actual = this.order()?.status;
    const definicion = this.statuses().find((s) => s.status === actual);
    if (!definicion) {
      return [];
    }
    return this.statuses().filter((s) => definicion.allowedTransitions.includes(s.status));
  }

  protected changeStatus(target: string): void {
    if (this.busy()) {
      return;
    }
    const etiqueta = this.statuses().find((s) => s.status === target)?.label ?? target;
    if (!confirm(`Cambiar el pedido a "${etiqueta}"? Se avisara al cliente.`)) {
      return;
    }
    this.run(this.api.changeStatus(this.id, target, this.statusNote || undefined),
      `Estado cambiado a "${etiqueta}". El cliente fue avisado.`);
    this.statusNote = '';
  }

  // ------------------------------------------------------------------ productos

  protected canEditItems(): boolean {
    const s = this.order()?.status;
    return s === 'PREPARANDO_ORDEN' || s === 'ALISTANDO_PEDIDO';
  }

  protected setQuantity(productId: string, value: string): void {
    const n = Number(value);
    this.quantities.update((q) => ({ ...q, [productId]: Number.isFinite(n) && n >= 0 ? n : 0 }));
  }

  protected saveItems(): void {
    if (this.busy()) {
      return;
    }
    // Las lineas con cantidad cero se eliminan del pedido.
    const items = Object.entries(this.quantities())
      .filter(([, qty]) => qty > 0)
      .map(([productId, quantity]) => ({ productId, quantity }));

    if (!items.length) {
      this.error.set('El pedido debe conservar al menos un producto.');
      return;
    }

    this.run(this.api.changeItems(this.id, items, this.itemsNote || undefined),
      'Productos actualizados. El cliente fue avisado del cambio.');
    this.itemsNote = '';
    this.editingItems.set(false);
  }

  protected cancelEdit(): void {
    const o = this.order();
    if (o) {
      this.resetQuantities(o);
    }
    this.editingItems.set(false);
  }

  private run(source: ReturnType<CompanyOrdersService['detail']>, ok: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);

    source.subscribe({
      next: (o) => {
        this.order.set(o);
        this.resetQuantities(o);
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
