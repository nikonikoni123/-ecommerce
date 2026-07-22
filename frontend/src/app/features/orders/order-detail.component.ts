import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { OrderService } from '../../core/cart.service';
import { formatDate, formatPrice } from '../../core/format';
import { OrderDetail } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

/** Los estados por los que pasa un pedido, para dibujar la linea de tiempo de la entrega. */
const RUTA_ENTREGA = [
  { status: 'PREPARANDO_ORDEN', label: 'Preparando orden' },
  { status: 'ALISTANDO_PEDIDO', label: 'Alistando pedido' },
  { status: 'ENVIANDO', label: 'Enviando' },
  { status: 'ENTREGADO', label: 'Entregado' },
];

@Component({
  selector: 'app-order-detail',
  imports: [RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.scss',
})
export class OrderDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(OrderService);

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly downloading = signal(false);

  protected readonly steps = RUTA_ENTREGA;
  protected readonly price = formatPrice;
  protected readonly date = formatDate;

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Pedido no encontrado.');
      this.loading.set(false);
      return;
    }

    this.api.detail(id).subscribe({
      next: (o) => {
        this.order.set(o);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar el pedido.'));
        this.loading.set(false);
      },
    });
  }

  /** Un paso esta cumplido si ya figura en el historial del pedido. */
  protected reached(status: string): boolean {
    return this.order()?.history.some((h) => h.status === status) ?? false;
  }

  /** El pedido se cancelo o reembolso: la ruta normal de entrega deja de aplicar. */
  protected get interrupted(): boolean {
    const o = this.order();
    return o ? o.status === 'CANCELADO' || o.status === 'REEMBOLSADO' : false;
  }

  protected downloadInvoice(): void {
    const o = this.order();
    if (!o || this.downloading()) {
      return;
    }
    this.downloading.set(true);

    this.api.invoice(o.id).subscribe({
      next: (blob) => {
        this.downloading.set(false);
        // Descarga forzada con nombre legible, en vez de abrir el PDF en una pestana nueva.
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `factura-${o.number}.pdf`;
        enlace.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.downloading.set(false);
        this.error.set(errorMessage(err, 'No pudimos descargar la factura.'));
      },
    });
  }
}
