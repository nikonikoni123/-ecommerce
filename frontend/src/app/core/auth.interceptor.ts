import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/** Endpoints que nunca deben provocar un intento de renovacion. */
const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/register', '/auth/verify'];

/**
 * Estado compartido de la renovacion. Si varias peticiones reciben un 401 a la vez, solo una
 * renueva el token y el resto espera al resultado, en lugar de disparar N renovaciones en paralelo
 * (que ademas fallarian, porque el refresh token rota en cada uso).
 */
let refreshing = false;
const refreshedToken = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);

  const isApiCall = request.url.startsWith(environment.apiBaseUrl);
  const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => request.url.includes(path));

  const token = auth.accessToken;
  const authorized =
    isApiCall && token && !isAuthEndpoint
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(authorized).pipe(
    catchError((error: unknown) => {
      const is401 = error instanceof HttpErrorResponse && error.status === 401;
      if (!is401 || !isApiCall || isAuthEndpoint || !auth.refreshToken) {
        return throwError(() => error);
      }
      return handleUnauthorized(auth, authorized, next);
    }),
  );
};

function handleUnauthorized(
  auth: AuthService,
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> {
  if (refreshing) {
    // Otra peticion ya esta renovando: esperar a que publique el token nuevo.
    return refreshedToken.pipe(
      filter((value): value is string => value !== null),
      take(1),
      switchMap((newToken) =>
        next(request.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } })),
      ),
    );
  }

  refreshing = true;
  refreshedToken.next(null);

  return auth.refreshSession().pipe(
    switchMap((response) => {
      refreshing = false;
      refreshedToken.next(response.accessToken);
      return next(request.clone({ setHeaders: { Authorization: `Bearer ${response.accessToken}` } }));
    }),
    catchError((refreshError: unknown) => {
      refreshing = false;
      auth.logout();
      return throwError(() => refreshError);
    }),
  );
}
