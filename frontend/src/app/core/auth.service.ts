import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, MessageResponse, Profile, UserSummary } from './models';

/**
 * Estado del recordatorio de 2FA dentro de la pestana actual: 'pending' mientras deba mostrarse y
 * 'dismissed' cuando el usuario lo aparta. Vive en sessionStorage para sobrevivir a una recarga
 * pero desaparecer al cerrar la pestana, de modo que reaparezca en el siguiente ingreso.
 *
 * Es lo unico que se guarda en el navegador: no es un dato sensible, solo una preferencia de
 * presentacion.
 */
const REMINDER_KEY = 'ecommerce.twoFactorReminder';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly base = environment.apiBaseUrl;

  /**
   * El usuario vive solo en memoria. La sesion la sostienen las cookies httpOnly que emite el
   * servidor, ilegibles desde JavaScript; al recargar la pagina se restaura con {@link restoreSession}.
   */
  private readonly currentUser = signal<UserSummary | null>(null);
  /** El recordatorio de 2FA se muestra en cada ingreso hasta que el usuario la active. */
  private readonly reminderVisible = signal(sessionStorage.getItem(REMINDER_KEY) === 'pending');

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly isCustomer = computed(() => this.currentUser()?.type === 'CUSTOMER');
  readonly isCompanyMember = computed(() => this.currentUser()?.type === 'COMPANY_MEMBER');
  readonly isRoot = computed(() => this.currentUser()?.root === true);
  readonly showTwoFactorReminder = this.reminderVisible.asReadonly();

  /** Comprueba un permiso concreto. El usuario root los tiene todos de forma implicita. */
  has(permission: string): boolean {
    const user = this.currentUser();
    if (!user) return false;
    return user.root || user.permissions.includes(permission);
  }

  // ------------------------------------------------------------------ registro

  registerCustomer(payload: unknown): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/auth/register/customer`, payload);
  }

  registerCompany(payload: unknown): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/auth/register/company`, payload);
  }

  verifyEmail(token: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/auth/verify`, null, {
      params: { token },
    });
  }

  resendVerification(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/auth/verify/resend`, null, {
      params: { email },
    });
  }

  // ------------------------------------------------------------------ sesion

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/login`, { email, password })
      .pipe(tap((response) => this.storeSession(response)));
  }

  loginTwoFactor(challengeToken: string, code: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/login/2fa`, { challengeToken, code })
      .pipe(tap((response) => this.storeSession(response)));
  }

  /** El token de renovacion viaja en su cookie; no hay nada que enviar en el cuerpo. */
  refreshSession(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/refresh`, {})
      .pipe(tap((response) => this.storeSession(response)));
  }

  public forceLogout(): void {
    this.currentUser.set(null); 
    sessionStorage.clear();
    window.location.href = '/auth/login?reason=session_conflict';
  }


  restoreSession(): Observable<UserSummary | null> {
    return this.http.get<UserSummary>(`${this.base}/auth/session`).pipe(
      tap((user) => this.currentUser.set(user)),
      catchError(() => {
        this.currentUser.set(null);
        return of(null);
      }),
    );
  }

  logout(navigate = true): void {
    // El cierre en el servidor es best effort: borra la cookie e invalida el token de renovacion.
    this.http.post(`${this.base}/auth/logout`, {}).subscribe({ error: () => undefined });
    this.clearSession();
    if (navigate) {
      this.router.navigate(['/auth/login']);
    }
  }

  /**
   * Guarda lo que el navegador si puede conocer: quien es el usuario, para pintar la interfaz. Los
   * tokens no llegan aqui —viajan en cookies httpOnly— y por eso no hay nada que almacenar.
   */
  private storeSession(response: AuthResponse): void {
    if (response.twoFactorRequired || !response.user) {
      // Todavia falta el segundo factor: aun no hay sesion.
      return;
    }
    this.currentUser.set(response.user);

    // Cada ingreso reabre el recordatorio, aunque se hubiera apartado en la sesion anterior.
    if (response.twoFactorReminder) {
      sessionStorage.setItem(REMINDER_KEY, 'pending');
    } else {
      sessionStorage.removeItem(REMINDER_KEY);
    }
    this.reminderVisible.set(response.twoFactorReminder);
  }

  clearSession(): void {
    sessionStorage.removeItem(REMINDER_KEY);
    this.currentUser.set(null);
    this.reminderVisible.set(false);
  }

  /** El aviso se oculta hasta el proximo ingreso, no de forma permanente. */
  dismissTwoFactorReminder(): void {
    sessionStorage.setItem(REMINDER_KEY, 'dismissed');
    this.reminderVisible.set(false);
  }

  // ------------------------------------------------------------------ perfil

  loadProfile(): Observable<Profile> {
    return this.http.get<Profile>(`${this.base}/me`).pipe(
      tap((profile) => {
        this.currentUser.update((user) =>
          user ? { ...user, twoFactorEnabled: profile.twoFactorEnabled } : user,
        );
        if (profile.twoFactorEnabled) {
          sessionStorage.removeItem(REMINDER_KEY);
          this.reminderVisible.set(false);
        }
      }),
    );
  }

}
