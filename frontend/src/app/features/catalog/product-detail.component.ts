import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { CartService } from '../../core/cart.service';
import { CatalogService } from '../../core/catalog.service';
import { formatPrice } from '../../core/format';
import { ProductDetail } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-product-detail',
  imports: [RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
})
export class ProductDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(CatalogService);
  private readonly cart = inject(CartService);
  protected readonly auth = inject(AuthService);

  protected readonly product = signal<ProductDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly quantity = signal(1);
  protected readonly adding = signal(false);
  protected readonly cartMessage = signal<string | null>(null);
  protected readonly cartError = signal<string | null>(null);

  protected readonly price = computed(() => {
    const item = this.product();
    return item ? formatPrice(item.price, item.currency) : '';
  });

  constructor() {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      this.error.set('Producto no encontrado.');
      this.loading.set(false);
      return;
    }

    this.catalog.detail(slug).subscribe({
      next: (item) => {
        this.product.set(item);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar el producto.'));
        this.loading.set(false);
      },
    });
  }

  protected setQuantity(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.quantity.set(Number.isFinite(value) && value > 0 ? Math.floor(value) : 1);
  }

  protected addToCart(productId: string): void {
    if (this.adding()) {
      return;
    }
    this.adding.set(true);
    this.cartMessage.set(null);
    this.cartError.set(null);

    this.cart.add(productId, this.quantity()).subscribe({
      next: () => {
        this.adding.set(false);
        this.cartMessage.set('Anadido a tu carrito.');
      },
      error: (err) => {
        this.adding.set(false);
        this.cartError.set(errorMessage(err, 'No pudimos anadirlo al carrito.'));
      },
    });
  }
}
