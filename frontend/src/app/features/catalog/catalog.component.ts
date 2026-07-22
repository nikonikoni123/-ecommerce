import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { CatalogFilters, CatalogService } from '../../core/catalog.service';
import { PageResponse, ProductSummary } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';
import { ProductCardComponent } from '../../shared/product-card.component';

@Component({
  selector: 'app-catalog',
  imports: [FormsModule, ProductCardComponent, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './catalog.component.html',
  styleUrl: './catalog.component.scss',
})
export class CatalogComponent {
  private readonly catalog = inject(CatalogService);
  protected readonly auth = inject(AuthService);

  protected readonly page = signal<PageResponse<ProductSummary> | null>(null);
  protected readonly categories = signal<string[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected search = '';
  protected category = '';
  protected sort = '';
  protected inStockOnly = false;

  private currentPage = 0;

  constructor() {
    this.catalog.categories().subscribe({
      next: (list) => this.categories.set(list),
      error: () => this.categories.set([]),
    });
    this.load();
  }

  protected applyFilters(): void {
    this.currentPage = 0;
    this.load();
  }

  protected clearFilters(): void {
    this.search = '';
    this.category = '';
    this.sort = '';
    this.inStockOnly = false;
    this.applyFilters();
  }

  protected goToPage(page: number): void {
    this.currentPage = page;
    this.load();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected pageNumbers(): number[] {
    const total = this.page()?.totalPages ?? 0;
    return Array.from({ length: total }, (_, index) => index);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);

    const filters: CatalogFilters = {
      search: this.search.trim() || undefined,
      category: this.category || undefined,
      sort: this.sort || undefined,
      inStockOnly: this.inStockOnly || undefined,
      page: this.currentPage,
      size: 12,
    };

    this.catalog.browse(filters).subscribe({
      next: (result) => {
        this.page.set(result);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar el catalogo.'));
        this.loading.set(false);
      },
    });
  }
}
