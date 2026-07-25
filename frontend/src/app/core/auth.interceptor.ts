import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { inject } from '@angular/core';


/** Endpoints que nunca deben provocar un intento de renovacion. */
const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/register', '/auth/verify', '/auth/session'];

/** Metodos que cambian estado y por tanto exigen el token anti-CSRF. */
const MUTATING = ['POST', 'PUT', 'PATCH', 'DELETE'];

const XSRF_COOKIE = 'XSRF-TOKEN';
const XSRF_HEADER = 'X-XSRF-TOKEN';

/**
 * Estado compartido de la renovacion. Si varias peticiones reciben un 401 a la vez, solo una
 * renueva y el resto espera al resultado, en lugar de disparar N renovaciones en paralelo (que
 * ademas fallarian, porque el token de renovacion rota en cada uso).
 *
 * Ya no se propaga ningun token: la sesion viaja en cookies httpOnly y el navegador las adjunta
 * solo. Basta con señalar que la renovacion termino.
 */
let refreshing = false;
const refreshDone = new BehaviorSubject<boolean>(false);

/** Lee una cookie no httpOnly. La de XSRF es legible a proposito: el cliente debe reenviarla. */
function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

/**
 * Adjunta la sesion. `withCredentials` hace que el navegador envie las cookies httpOnly aunque la
 * API viva en otro origen, y la cabecera XSRF demuestra que la peticion la origina nuestra propia
 * pagina: un sitio ajeno no puede leer la cookie y por tanto no puede construirla.
 */
function withSessionCookies(request: HttpRequest<unknown>): HttpRequest<unknown> {
  const token = readCookie(XSRF_COOKIE);
  const needsCsrf = MUTATING.includes(request.method) && token !== null;

  return request.clone({
    withCredentials: true,
    ...(needsCsrf ? { setHeaders: { [XSRF_HEADER]: token } } : {}),
  });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const authReq = withSessionCookies(req);

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 403 && authService.isAuthenticated()) {
        console.warn('Conflicto de identidad detectado (403).');
        authService.forceLogout();
        return throwError(() => error);
      }
      if (error.status === 401) {
        const isAuthUrl = AUTH_ENDPOINTS.some(url => req.url.includes(url));
        if (!isAuthUrl && authService.isAuthenticated()) {
          return handleUnauthorized(authService, req, next);
        } 
        if (authService.isAuthenticated()) {
          authService.forceLogout();
        }
      }

      return throwError(() => error);
    })
  );
};

function handleUnauthorized(
  auth: AuthService,
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> {
  if (refreshing) {
    return refreshDone.pipe(
      filter((done) => done),
      take(1),
      switchMap(() => next(withSessionCookies(request))),
    );
  }

  refreshing = true;
  refreshDone.next(false);

  return auth.refreshSession().pipe(
    switchMap(() => {
      refreshing = false;
      refreshDone.next(true);
      return next(withSessionCookies(request));
    }),
    catchError((err) => {
      refreshing = false;
      refreshDone.next(true);
      auth.forceLogout();
      return throwError(() => err);
    }),
  );
}
