import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/**
 * Extrae un mensaje presentable de un fallo HTTP.
 *
 * El backend devuelve siempre un {@link ApiError}, pero hay que cubrir tambien el caso en que la
 * peticion ni siquiera llego (servidor caido, CORS), donde no hay cuerpo que leer.
 */
export function errorMessage(error: unknown, fallback = 'Ocurrio un error. Intentalo de nuevo.'): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (error.status === 0) {
    return 'No pudimos conectar con el servidor. Comprueba que la API este disponible.';
  }

  const body = error.error as Partial<ApiError> | string | null;
  if (typeof body === 'string' && body.trim()) {
    return body;
  }
  if (body && typeof body === 'object' && body.message) {
    return body.message;
  }
  return fallback;
}

/** Errores por campo devueltos por la validacion, para pintarlos junto a cada input. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && body.fieldErrors) {
      return body.fieldErrors;
    }
  }
  return {};
}
