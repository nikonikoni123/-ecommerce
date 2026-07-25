import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styleUrl: './auth-form.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  /** Formulario del segundo factor, visible solo cuando el backend devuelve un reto. */
  protected readonly codeForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly challengeToken = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: (response) => {
        this.submitting.set(false);
        if (response.twoFactorRequired) {
          this.challengeToken.set(response.challengeToken);
          return;
        }
        this.redirect();
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(errorMessage(err, 'No pudimos iniciar tu sesion.'));
      },
    });
  }

  protected submitCode(): void {
    const token = this.challengeToken();
    if (this.codeForm.invalid || !token || this.submitting()) {
      this.codeForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    this.auth.loginTwoFactor(token, this.codeForm.getRawValue().code).subscribe({
      next: () => {
        this.submitting.set(false);
        this.redirect();
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(errorMessage(err, 'El codigo no es correcto.'));
      },
    });
  }

  ngOnInit() {
    const params = this.route.snapshot.queryParams;
    if (params['reason'] === 'session_conflict') {
      this.error.set('Se detectó un cambio de usuario en otra pestaña. Por favor, ingresa de nuevo.');
    }
  }

  /** Vuelve a la pantalla que el usuario intentaba abrir antes de que le pidieramos la sesion. */
  private redirect(): void {
    const target = this.route.snapshot.queryParamMap.get('redirect') ?? '/';
    this.router.navigateByUrl(target);
  }
}
