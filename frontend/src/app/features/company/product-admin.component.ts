import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../core/api-error';
import { CompanyCatalogService } from '../../core/catalog.service';
import { formatPrice } from '../../core/format';
import { HasPermissionDirective } from '../../core/has-permission.directive';
import { CompanyProduct, PageResponse, Permission } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';
import { CatalogService } from '../../core/catalog.service';

@Component({
  selector: 'app-product-admin',
  imports: [ReactiveFormsModule, AlertComponent, HasPermissionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-admin.component.html',
  styleUrl: './product-admin.component.scss',
})
export class ProductAdminComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(CompanyCatalogService);

  protected readonly Permission = Permission;

  protected readonly page = signal<PageResponse<CompanyProduct> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  /** Producto en edicion. Nulo cuando el formulario esta cerrado. */
  protected readonly editing = signal<CompanyProduct | null>(null);
  protected readonly formOpen = signal(false);
  private readonly service = inject(CatalogService);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.maxLength(5000)]],
    price: [0, [Validators.required, Validators.min(0.01)]],
    stock: [0, [Validators.required, Validators.min(0)]],
    categories: [''],
    visibleTags: [''],
    hiddenTags: [''],
    images: [''],
    active: [true],
  });

  protected readonly price = formatPrice;

  protected readonly currentPage = signal(0);

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api.list(this.currentPage(), 20).subscribe({
      next: (result) => {
        this.page.set(result);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar tus productos.'));
        this.loading.set(false);
      },
    });
  }

  protected goToPage(page: number): void {
    this.currentPage.set(page);
    this.load();
  }

  protected get pageNumbers(): number[] {
    const total = this.page()?.totalPages ?? 0;
    return Array.from({ length: total }, (_, i) => i);
  }

  // ---------------------------------------------------------------- formulario

  protected openCreate(): void {
    this.editing.set(null);
    this.form.reset({ price: 0, stock: 0, active: true });
    this.formOpen.set(true);
    this.clearBanners();
  }

  protected openEdit(product: CompanyProduct): void {
    this.editing.set(product);
    this.form.reset({
      name: product.name,
      description: product.description,
      price: product.price,
      stock: product.stock,
      categories: product.categories.join(', '),
      visibleTags: product.visibleTags.join(', '),
      hiddenTags: product.hiddenTags.join(', '),
      images: product.images.join(', '),
      active: product.active,
    });
    this.formOpen.set(true);
    this.clearBanners();
  }

  protected closeForm(): void {
    this.formOpen.set(false);
    this.editing.set(null);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.clearBanners();

    const raw = this.form.getRawValue();
    const payload = {
      name: raw.name,
      description: raw.description,
      price: raw.price,
      stock: raw.stock,
      active: raw.active,
      categories: this.toList(raw.categories),
      visibleTags: this.toList(raw.visibleTags),
      hiddenTags: this.toList(raw.hiddenTags),
      images: this.toList(raw.images),
    };

    const current = this.editing();
    const request = current
      ? this.api.update(current.id, payload)
      : this.api.create(payload);

    request.subscribe({
      next: () => {
        this.message.set(current ? 'Producto actualizado.' : 'Producto creado.');
        this.closeForm();
        this.load();
      },
      error: (err) => this.error.set(errorMessage(err, 'No pudimos guardar el producto.')),
    });
  }

  // ---------------------------------------------------------------- stock y baja

  protected changeStock(product: CompanyProduct, value: string): void {
    const stock = Number(value);
    if (!Number.isInteger(stock) || stock < 0) {
      this.error.set('El stock debe ser un numero entero mayor o igual que cero.');
      return;
    }
    this.clearBanners();

    this.api.updateStock(product.id, stock).subscribe({
      next: (updated) => {
        this.message.set(`Stock de "${updated.name}" actualizado a ${updated.stock}.`);
        this.load();
      },
      error: (err) => this.error.set(errorMessage(err, 'No pudimos actualizar el stock.')),
    });
  }

  protected remove(product: CompanyProduct): void {
    if (!confirm(`Eliminar "${product.name}"? Esta accion no se puede deshacer.`)) {
      return;
    }
    this.clearBanners();

    this.api.remove(product.id).subscribe({
      next: () => {
        this.message.set('Producto eliminado.');
        this.load();
      },
      error: (err) => this.error.set(errorMessage(err, 'No pudimos eliminar el producto.')),
    });
  }

  /** Convierte "uno, dos" en ["uno","dos"], descartando los vacios. */
  private toList(value: string): string[] {
    return value
      .split(',')
      .map((entry) => entry.trim())
      .filter((entry) => entry.length > 0);
  }

  private clearBanners(): void {
    this.error.set(null);
    this.message.set(null);
  }
}
