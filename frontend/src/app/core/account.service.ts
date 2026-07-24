import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AppNotification, MessageResponse, PageResponse, Profile, TwoFactorSetup } from './models';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  profile(): Observable<Profile> {
    return this.http.get<Profile>(`${this.base}/me`);
  }

  updateProfile(payload: unknown): Observable<Profile> {
    return this.http.patch<Profile>(`${this.base}/me`, payload);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/me/password`, {
      currentPassword,
      newPassword,
    });
  }

  startTwoFactorSetup(): Observable<TwoFactorSetup> {
    return this.http.post<TwoFactorSetup>(`${this.base}/me/2fa/setup`, {});
  }

  enableTwoFactor(code: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/me/2fa/enable`, { code });
  }

  disableTwoFactor(code: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/me/2fa/disable`, { code });
  }

  deleteAccount(password: string): Observable<MessageResponse> {
    // DELETE con cuerpo: la baja exige confirmar la contrasena.
    return this.http.delete<MessageResponse>(`${this.base}/me`, { body: { password } });
  }

  forgotPassword(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/auth/password/forgot`, { email });
  }

  resetPassword(token: string, password: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/auth/password/reset`, { token, password });
  }
  
  toggle2fa(enable: boolean): Observable<Profile> {
    return this.http.patch<Profile>(`${this.base}/me/2fa/toggle?enable=${enable}`, {});
  }
}

@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/notifications`;

  list(page = 0, size = 20): Observable<PageResponse<AppNotification>> {
    return this.http.get<PageResponse<AppNotification>>(this.base, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  unreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/unread-count`);
  }

  markRead(id: string): Observable<MessageResponse> {
    return this.http.patch<MessageResponse>(`${this.base}/${id}/read`, {});
  }
}
