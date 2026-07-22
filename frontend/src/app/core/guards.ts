import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Exige una sesion iniciada y recuerda a donde queria ir el usuario. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/auth/login'], {
    queryParams: { redirect: state.url },
  });
};

/** Exige que la cuenta sea de empresa. */
export const companyGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/auth/login']);
  }
  return auth.isCompanyMember() ? true : router.createUrlTree(['/']);
};

/**
 * Exige un permiso concreto, declarado en la ruta como {@code data: { permission: '...' }}.
 * Es la contraparte en el enrutador de las comprobaciones del backend.
 */
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const permission = route.data['permission'] as string | undefined;
  if (!permission) {
    return true;
  }
  return auth.has(permission) ? true : router.createUrlTree(['/']);
};

/** Impide volver al login o al registro con la sesion ya iniciada. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/']) : true;
};
