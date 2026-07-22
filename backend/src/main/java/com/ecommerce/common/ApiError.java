package com.ecommerce.common;

import java.time.Instant;
import java.util.Map;

/**
 * Cuerpo de error uniforme para toda la API.
 *
 * @param code       identificador estable para que el frontend reaccione sin depender del texto
 * @param message    mensaje legible, en espanol, apto para mostrar al usuario
 * @param fieldErrors errores por campo en fallos de validacion, vacio en el resto de los casos
 */
public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of(), Instant.now());
    }

    public static ApiError validation(String message, Map<String, String> fieldErrors) {
        return new ApiError("VALIDATION_ERROR", message, fieldErrors, Instant.now());
    }
}
