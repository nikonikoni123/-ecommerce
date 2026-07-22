import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { AlertComponent } from '../../shared/alert.component';

/** Pantalla a la que apunta el enlace del correo de verificacion. */
@Component({
  selector: 'app-verify',
  imports: [RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './verify.component.html',
  styleUrl: './auth-form.scss',
})
export class VerifyComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  protected readonly verifying = signal(true);
  protected readonly success = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.verifying.set(false);
      this.error.set('El enlace no incluye un token de verificacion.');
      return;
    }

    this.auth.verifyEmail(token).subscribe({
      next: (response) => {
        this.verifying.set(false);
        this.success.set(response.message);
      },
      error: (err) => {
        this.verifying.set(false);
        this.error.set(errorMessage(err, 'No pudimos confirmar tu correo.'));
      },
    });
  }
}
