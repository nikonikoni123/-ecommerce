import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AccountService } from '../../core/account.service';
import { errorMessage } from '../../core/api-error';
import { AlertComponent } from '../../shared/alert.component';

/**
 * Destino del enlace de restablecimiento. Tambien es la pantalla donde los usuarios adicionales
 * invitados por root establecen su primera contrasena.
 */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="auth">
      <div class="auth__panel">
        <p class="eyebrow">Acceso</p>
        <h1 class="auth__title">Elegir contrasena</h1>

        @if (done()) {
          <app-alert [message]="done()" variant="success" />
          <div class="auth__links"><a routerLink="/auth/login">Ingresar</a></div>
        } @else if (!token) {
          <app-alert message="El enlace no incluye un token valido." variant="error" />
          <div class="auth__links"><a routerLink="/auth/recuperar">Solicitar uno nuevo</a></div>
        } @else {
          <app-alert [message]="error()" variant="error" />

          <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <div class="field">
              <label class="field__label" for="password">Contrasena nueva</label>
              <input
                class="input"
                id="password"
                type="password"
                formControlName="password"
                autocomplete="new-password"
                [class.is-invalid]="form.controls.password.touched && form.controls.password.invalid"
              />
              <span class="field__hint">Minimo 8 caracteres.</span>
              @if (form.controls.password.touched && form.controls.password.invalid) {
                <span class="field__error">La contrasena debe tener al menos 8 caracteres.</span>
              }
            </div>

            <button class="btn btn--block" type="submit" [disabled]="submitting()">
              {{ submitting() ? 'Guardando...' : 'Guardar contrasena' }}
            </button>
          </form>
        }
      </div>
    </section>
  `,
  styleUrl: './auth-form.scss',
})
export class ResetPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly account = inject(AccountService);
  private readonly route = inject(ActivatedRoute);

  protected readonly token = this.route.snapshot.queryParamMap.get('token');

  protected readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid || !this.token || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    this.account.resetPassword(this.token, this.form.getRawValue().password).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.done.set(response.message);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(errorMessage(err, 'No pudimos actualizar la contrasena.'));
      },
    });
  }
}
