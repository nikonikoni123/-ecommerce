import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../../core/catalog.service';
import { ProductSummary } from '../../core/models';
import { ProductCardComponent } from '../../shared/product-card.component';

@Component({
  selector: 'app-home',
  imports: [RouterLink, ProductCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private readonly catalog = inject(CatalogService);

  protected readonly featured = signal<ProductSummary[]>([]);
  protected readonly loading = signal(true);

  /** Los cuatro pasos de la rutina, el recurso narrativo de la referencia visual. */
  protected readonly steps = [
    { number: '01', title: 'Limpia', text: 'Retira el exceso sin alterar la barrera cutanea.' },
    { number: '02', title: 'Activa', text: 'Concentrados de alta tolerancia para cada objetivo.' },
    { number: '03', title: 'Recupera', text: 'Hidratacion que refuerza y sostiene la barrera.' },
    { number: '04', title: 'Protege', text: 'Filtro diario de amplio espectro, acabado invisible.' },
  ];

  constructor() {
    this.catalog.browse({ size: 4 }).subscribe({
      next: (page) => {
        this.featured.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
