import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage, fieldErrors } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-register-company',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register-company.component.html',
  styleUrl: './auth-form.scss',
})
export class RegisterCompanyComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly form = this.fb.nonNullable.group({
    companyName: ['', [Validators.required, Validators.maxLength(150)]],
    legalRepresentative: ['', [Validators.required, Validators.maxLength(150)]],
    nit: ['', [Validators.required, Validators.maxLength(40)]],
    address: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email]],
    postalCode: ['', [Validators.required, Validators.maxLength(20)]],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
    phone: ['', [Validators.required, Validators.maxLength(30)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    /** Etiquetas separadas por coma. Opcional. */
    categoryTags: [''],
    additionalUsers: this.fb.array<ReturnType<typeof this.buildExtraUser>>([]),
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal(false);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected get additionalUsers(): FormArray {
    return this.form.controls.additionalUsers as unknown as FormArray;
  }

  private buildExtraUser() {
    return this.fb.nonNullable.group({
      name: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
    });
  }

  protected addUser(): void {
    this.additionalUsers.push(this.buildExtraUser());
  }

  protected removeUser(index: number): void {
    this.additionalUsers.removeAt(index);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.serverErrors.set({});

    const raw = this.form.getRawValue();
    const payload = {
      ...raw,
      // La cadena "ropa, algodon" se convierte en la lista que espera la API.
      categoryTags: raw.categoryTags
        .split(',')
        .map((tag) => tag.trim())
        .filter((tag) => tag.length > 0),
    };

    this.auth.registerCompany(payload).subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(errorMessage(err, 'No pudimos registrar la empresa.'));
        this.serverErrors.set(fieldErrors(err));
      },
    });
  }

  protected fieldError(name: string): string | null {
    return this.serverErrors()[name] ?? null;
  }
}
