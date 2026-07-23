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
 * Exige permisos declarados en la ruta. Acepta {@code data: { permission: '...' }} para exigir uno
 * concreto, o {@code data: { anyPermission: ['A', 'B'] }} para exigir al menos uno de la lista. Es la
 * contraparte en el enrutador de las comprobaciones del backend.
 */
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const permission = route.data['permission'] as string | undefined;
  const anyPermission = route.data['anyPermission'] as string[] | undefined;

  if (permission && !auth.has(permission)) {
    return router.createUrlTree(['/']);
  }
  if (anyPermission && !anyPermission.some((p) => auth.has(p))) {
    return router.createUrlTree(['/']);
  }
  return true;
};

/** Impide volver al login o al registro con la sesion ya iniciada. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/']) : true;
};
