import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { CartService, OrderService } from '../../core/cart.service';
import { formatPrice } from '../../core/format';
import { SurpriseProposal } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-surprise',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './surprise.component.html',
  styleUrl: './surprise.component.scss',
})
export class SurpriseComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(OrderService);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  protected readonly proposal = signal<SurpriseProposal | null>(null);
  protected readonly loading = signal(false);
  protected readonly adding = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly price = formatPrice;

  protected readonly form = this.fb.nonNullable.group({
    items: [3, [Validators.required, Validators.min(1), Validators.max(10)]],
    maxTotal: [null as number | null],
  });

  protected generate(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const { items, maxTotal } = this.form.getRawValue();

    this.api.surprise(items, maxTotal ?? undefined).subscribe({
      next: (p) => {
        this.proposal.set(p);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(errorMessage(err, 'No pudimos armar la caja sorpresa.'));
      },
    });
  }

  /**
   * Lleva la propuesta al carrito. Se vacia antes para que el pedido sorpresa no se mezcle con una
   * compra normal: el descuento del 50% se concede a cambio de aceptar la seleccion completa.
   */
  protected accept(): void {
    const p = this.proposal();
    if (!p || this.adding()) {
      return;
    }
    this.adding.set(true);
    this.error.set(null);

    // Se vacia y se anade el lote entero en una sola peticion: mandar un alta por producto en
    // paralelo hace que las escrituras se pisen y solo quede la ultima.
    this.cart.clear().subscribe({
      next: () => {
        const items = p.items.map((i) => ({ productId: i.productId, quantity: i.quantity }));
        this.cart.addMany(items).subscribe({
          next: () => {
            this.adding.set(false);
            this.router.navigate(['/carrito']);
          },
          error: (err) => this.fail(err),
        });
      },
      error: (err) => this.fail(err),
    });
  }

  private fail(err: unknown): void {
    this.adding.set(false);
    this.error.set(errorMessage(err, 'No pudimos pasar la caja al carrito.'));
  }
}
