import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { CartService } from '../../core/cart.service';
import { CatalogService } from '../../core/catalog.service';
import { formatPrice } from '../../core/format';
import { CartView, ProductSummary } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-cart',
  imports: [RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss',
})
export class CartComponent {
  private readonly cart = inject(CartService);
  private readonly catalog = inject(CatalogService);
  private readonly router = inject(Router);

  protected readonly view = signal<CartView | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  /** Linea cuyo selector de sustitucion esta abierto. */
  protected readonly replacing = signal<string | null>(null);
  protected readonly alternatives = signal<ProductSummary[]>([]);

  protected readonly price = formatPrice;

  constructor() {
    this.load();
  }

  private load(): void {
    this.cart.view().subscribe({
      next: (v) => {
        this.view.set(v);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar tu carrito.'));
        this.loading.set(false);
      },
    });
  }

  protected changeQuantity(productId: string, quantity: number): void {
    if (quantity < 1 || this.busy()) {
      return;
    }
    this.run(this.cart.updateQuantity(productId, quantity));
  }

  protected remove(productId: string): void {
    this.run(this.cart.remove(productId));
  }

  protected clear(): void {
    if (!confirm('Vaciar el carrito por completo?')) {
      return;
    }
    this.busy.set(true);
    this.cart.clear().subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: (err) => this.fail(err),
    });
  }

  /** Abre el selector para cambiar el producto de una linea por otro del catalogo. */
  protected startReplace(productId: string): void {
    if (this.replacing() === productId) {
      this.replacing.set(null);
      return;
    }
    this.replacing.set(productId);
    this.alternatives.set([]);

    this.catalog.browse({ size: 12, inStockOnly: true }).subscribe({
      next: (page) =>
        this.alternatives.set(page.content.filter((p) => p.id !== productId)),
      error: () => this.alternatives.set([]),
    });
  }

  protected confirmReplace(newProductId: string, quantity: number): void {
    const current = this.replacing();
    if (!current) {
      return;
    }
    this.replacing.set(null);
    this.run(this.cart.replace(current, newProductId, quantity));
  }

  protected goToCheckout(): void {
    this.router.navigate(['/checkout']);
  }

  private run(source: ReturnType<CartService['view']>): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: (v) => {
        this.view.set(v);
        this.busy.set(false);
      },
      error: (err) => this.fail(err),
    });
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.error.set(errorMessage(err, 'No pudimos actualizar el carrito.'));
    // El servidor manda: se recarga para no dejar la pantalla desincronizada.
    this.load();
  }
}
