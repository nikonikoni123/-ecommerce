import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, MessageResponse, Profile, UserSummary } from './models';

const ACCESS_TOKEN_KEY = 'ecommerce.accessToken';
const REFRESH_TOKEN_KEY = 'ecommerce.refreshToken';
const USER_KEY = 'ecommerce.user';
/**
 * Estado del recordatorio de 2FA dentro de la pestana actual: 'pending' mientras deba mostrarse y
 * 'dismissed' cuando el usuario lo aparta. Vive en sessionStorage para sobrevivir a una recarga
 * pero desaparecer al cerrar la pestana, de modo que reaparezca en el siguiente ingreso.
 */
const REMINDER_KEY = 'ecommerce.twoFactorReminder';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly base = environment.apiBaseUrl;

  private readonly currentUser = signal<UserSummary | null>(this.readStoredUser());
  /** El recordatorio de 2FA se muestra en cada ingreso hasta que el usuario la active. */
  private readonly reminderVisible = signal(sessionStorage.getItem(REMINDER_KEY) === 'pending');

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly isCustomer = computed(() => this.currentUser()?.type === 'CUSTOMER');
  readonly isCompanyMember = computed(() => this.currentUser()?.type === 'COMPANY_MEMBER');
  readonly isRoot = computed(() => this.currentUser()?.root === true);
  readonly showTwoFactorReminder = this.reminderVisible.asReadonly();

  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

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

  refreshSession(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/refresh`, { refreshToken: this.refreshToken })
      .pipe(tap((response) => this.storeSession(response)));
  }

  logout(navigate = true): void {
    const token = this.refreshToken;
    if (token) {
      // El cierre en el servidor es best effort: la sesion local se limpia en cualquier caso.
      this.http.post(`${this.base}/auth/logout`, { refreshToken: token }).subscribe({
        error: () => undefined,
      });
    }
    this.clearSession();
    if (navigate) {
      this.router.navigate(['/auth/login']);
    }
  }

  private storeSession(response: AuthResponse): void {
    if (response.twoFactorRequired || !response.accessToken) {
      // Todavia falta el segundo factor: no hay sesion que guardar.
      return;
    }
    localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
    if (response.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    }
    if (response.user) {
      localStorage.setItem(USER_KEY, JSON.stringify(response.user));
      this.currentUser.set(response.user);
    }
    // Cada ingreso reabre el recordatorio, aunque se hubiera apartado en la sesion anterior.
    if (response.twoFactorReminder) {
      sessionStorage.setItem(REMINDER_KEY, 'pending');
    } else {
      sessionStorage.removeItem(REMINDER_KEY);
    }
    this.reminderVisible.set(response.twoFactorReminder);
  }

  clearSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
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

  private readStoredUser(): UserSummary | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as UserSummary;
    } catch {
      localStorage.removeItem(USER_KEY);
      return null;
    }
  }
}
