import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../../core/catalog.service';
import { ProductSummary } from '../../core/models';
import { ProductCardComponent } from '../../shared/product-card.component';
import { AuthService } from '../../core/auth.service';

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
    { number: '01', title: 'CALIDAD', text: 'Seleccionamos cada artículo bajo estrictos estándares para asegurar durabilidad y diseño.' },
    { number: '02', title: 'AGILIDAD', text: 'Olvídate de las esperas largas. Procesamos y despachamos tu pedido en menos de 24 horas.' },
    { number: '03', title: 'SOPORTE', text: 'Atención personalizada 24/7. Estamos aquí para resolver cualquier duda sobre tu pedido.' },
    { number: '04', title: 'COMUNIDAD', text: 'Únete a miles de clientes satisfechos. Accede a descuentos preventa y ofertas exclusivas.' },
  ];

  constructor(public auth: AuthService) {
    this.catalog.browse({ size: 4 }).subscribe({
      next: (page) => {
        this.featured.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
