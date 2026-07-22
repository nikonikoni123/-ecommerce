import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountService } from '../../core/account.service';
import { errorMessage } from '../../core/api-error';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="auth">
      <div class="auth__panel">
        <p class="eyebrow">Acceso</p>
        <h1 class="auth__title">Recuperar contrasena</h1>

        @if (done()) {
          <app-alert [message]="done()" variant="success" />
          <div class="auth__links"><a routerLink="/auth/login">Volver a ingresar</a></div>
        } @else {
          <app-alert [message]="error()" variant="error" />
          <p class="auth__hint">
            Escribe tu correo y te enviaremos un enlace para elegir una contrasena nueva.
          </p>

          <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <div class="field">
              <label class="field__label" for="email">Correo</label>
              <input
                class="input"
                id="email"
                type="email"
                formControlName="email"
                autocomplete="email"
                [class.is-invalid]="form.controls.email.touched && form.controls.email.invalid"
              />
              @if (form.controls.email.touched && form.controls.email.invalid) {
                <span class="field__error">Escribe un correo valido.</span>
              }
            </div>

            <button class="btn btn--block" type="submit" [disabled]="submitting()">
              {{ submitting() ? 'Enviando...' : 'Enviar enlace' }}
            </button>
          </form>

          <div class="auth__links"><a routerLink="/auth/login">Volver a ingresar</a></div>
        }
      </div>
    </section>
  `,
  styleUrl: './auth-form.scss',
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly account = inject(AccountService);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    this.account.forgotPassword(this.form.getRawValue().email).subscribe({
      next: (response) => {
        this.submitting.set(false);
        // La respuesta es identica exista o no la cuenta, para no revelar correos registrados.
        this.done.set(response.message);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }
}
