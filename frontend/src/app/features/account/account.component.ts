import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AccountService } from '../../core/account.service';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { Profile, TwoFactorSetup } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-account',
  imports: [ReactiveFormsModule, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './account.component.html',
  styleUrl: './account.component.scss',
})
export class AccountComponent {
  private readonly fb = inject(FormBuilder);
  private readonly account = inject(AccountService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly profile = signal<Profile | null>(null);
  protected readonly loading = signal(true);

  protected readonly profileForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    lastName: [''],
    address: ['', [Validators.required]],
    postalCode: ['', [Validators.required]],
    phone: ['', [Validators.required]],
    gender: [''],
  });

  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected readonly twoFactorForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected readonly deleteForm = this.fb.nonNullable.group({
    password: ['', [Validators.required]],
  });

  protected readonly profileMessage = signal<string | null>(null);
  protected readonly profileError = signal<string | null>(null);
  protected readonly passwordMessage = signal<string | null>(null);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly twoFactorMessage = signal<string | null>(null);
  protected readonly twoFactorError = signal<string | null>(null);
  protected readonly deleteError = signal<string | null>(null);

  protected readonly setup = signal<TwoFactorSetup | null>(null);
  protected readonly confirmingDelete = signal(false);

  constructor() {
    this.loadProfile();
  }

  private loadProfile(): void {
    this.account.profile().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.profileForm.patchValue({
          firstName: profile.firstName ?? '',
          lastName: profile.lastName ?? '',
          address: profile.address ?? '',
          postalCode: profile.postalCode ?? '',
          phone: profile.phone ?? '',
          gender: profile.gender ?? '',
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  // ---------------------------------------------------------------- datos del usuario

  protected saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.profileMessage.set(null);
    this.profileError.set(null);

    this.account.updateProfile(this.profileForm.getRawValue()).subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.profileMessage.set('Datos actualizados.');
      },
      error: (err) => this.profileError.set(errorMessage(err, 'No pudimos guardar los cambios.')),
    });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.passwordMessage.set(null);
    this.passwordError.set(null);

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.account.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordForm.reset();
        this.passwordMessage.set('Contrasena actualizada. Se cerraron tus otras sesiones.');
      },
      error: (err) => this.passwordError.set(errorMessage(err, 'No pudimos cambiar la contrasena.')),
    });
  }

  // ---------------------------------------------------------------- verificacion en dos pasos

  protected startTwoFactor(): void {
    this.twoFactorError.set(null);
    this.twoFactorMessage.set(null);

    this.account.startTwoFactorSetup().subscribe({
      next: (setup) => this.setup.set(setup),
      error: (err) => this.twoFactorError.set(errorMessage(err, 'No pudimos iniciar la activacion.')),
    });
  }

  protected confirmTwoFactor(): void {
    if (this.twoFactorForm.invalid) {
      this.twoFactorForm.markAllAsTouched();
      return;
    }
    this.twoFactorError.set(null);

    this.account.enableTwoFactor(this.twoFactorForm.getRawValue().code).subscribe({
      next: () => {
        this.setup.set(null);
        this.twoFactorForm.reset();
        this.twoFactorMessage.set('Verificacion en dos pasos activada.');
        this.auth.loadProfile().subscribe({ error: () => undefined });
        this.loadProfile();
      },
      error: (err) => this.twoFactorError.set(errorMessage(err, 'El codigo no es correcto.')),
    });
  }

  protected disableTwoFactor(): void {
    if (this.twoFactorForm.invalid) {
      this.twoFactorForm.markAllAsTouched();
      return;
    }
    this.twoFactorError.set(null);

    this.account.disableTwoFactor(this.twoFactorForm.getRawValue().code).subscribe({
      next: () => {
        this.twoFactorForm.reset();
        this.twoFactorMessage.set('Verificacion en dos pasos desactivada.');
        this.loadProfile();
      },
      error: (err) => this.twoFactorError.set(errorMessage(err, 'El codigo no es correcto.')),
    });
  }

  /** Enlace a un generador de QR no disponible sin conexion: se muestra el secreto como respaldo. */
  protected otpUri(): string {
    return this.setup()?.otpAuthUri ?? '';
  }

  // ---------------------------------------------------------------- baja de la cuenta

  protected deleteAccount(): void {
    if (this.deleteForm.invalid) {
      this.deleteForm.markAllAsTouched();
      return;
    }
    this.deleteError.set(null);

    this.account.deleteAccount(this.deleteForm.getRawValue().password).subscribe({
      next: () => {
        this.auth.clearSession();
        this.router.navigate(['/']);
      },
      error: (err) => this.deleteError.set(errorMessage(err, 'No pudimos eliminar la cuenta.')),
    });
  }
}
