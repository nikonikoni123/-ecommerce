import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage, fieldErrors } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-register-customer',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register-customer.component.html',
  styleUrl: './auth-form.scss',
})
export class RegisterCustomerComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  /** Los obligatorios reproducen exactamente los exigidos por la especificacion. */
  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    username: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9._-]{3,30}$/)]],
    address: ['', [Validators.required, Validators.maxLength(200)]],
    postalCode: ['', [Validators.required, Validators.maxLength(20)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, Validators.maxLength(30)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    gender: [''],
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal(false);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.serverErrors.set({});

    this.auth.registerCustomer(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(errorMessage(err, 'No pudimos crear tu cuenta.'));
        this.serverErrors.set(fieldErrors(err));
      },
    });
  }

  protected fieldError(name: string): string | null {
    return this.serverErrors()[name] ?? null;
  }
}
