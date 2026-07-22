import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
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

  protected readonly product = signal<ProductDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

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
}
