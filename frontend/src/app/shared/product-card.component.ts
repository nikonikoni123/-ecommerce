import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatPrice } from '../core/format';
import { ProductSummary } from '../core/models';

/** Tarjeta del catalogo: imagen, nombre en mayusculas, precio y estado de stock. */
@Component({
  selector: 'app-product-card',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="card-product" [routerLink]="['/productos', product().slug]">
      <div class="card-product__media">
        @if (product().images.length) {
          <img [src]="product().images[0]" [alt]="product().name" loading="lazy" />
        } @else {
          <!-- Sin fotografia se muestra la inicial: mantiene la rejilla alineada. -->
          <span class="card-product__placeholder" aria-hidden="true">
            {{ product().name.charAt(0) }}
          </span>
        }
        @if (!product().inStock) {
          <span class="card-product__flag">Agotado</span>
        }
      </div>

      <div class="card-product__body">
        <p class="card-product__company">{{ product().companyName }}</p>
        <h3 class="card-product__name">{{ product().name }}</h3>
        <p class="card-product__price">{{ price() }}</p>
      </div>
    </a>
  `,
  styles: `
    @use 'styles/tokens' as *;

    .card-product {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      overflow: hidden;
      transition: border-color var(--transition);

      &:hover {
        border-color: var(--ink-muted);
      }
    }

    .card-product__media {
      position: relative;
      aspect-ratio: 4 / 5;
      background: var(--surface-alt);
      display: grid;
      place-items: center;
      overflow: hidden;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .card-product__placeholder {
      font-size: var(--text-3xl);
      font-weight: 300;
      color: var(--accent);
    }

    .card-product__flag {
      position: absolute;
      top: var(--space-3);
      left: var(--space-3);
      padding: var(--space-1) var(--space-3);
      background: var(--surface);
      border: 1px solid var(--line);
      font-size: var(--text-xs);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
      color: var(--ink-muted);
    }

    .card-product__body {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      padding: var(--space-6);
      flex: 1;
    }

    .card-product__company {
      @include eyebrow;
    }

    .card-product__name {
      font-size: var(--text-sm);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
      font-weight: 500;
      line-height: 1.4;
    }

    .card-product__price {
      margin-top: auto;
      font-size: var(--text-sm);
      color: var(--ink-soft);
    }
  `,
})
export class ProductCardComponent {
  readonly product = input.required<ProductSummary>();

  protected readonly price = computed(() =>
    formatPrice(this.product().price, this.product().currency),
  );
}
