package com.ecommerce.auth.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contratos de entrada y salida de los endpoints de autenticacion.
 *
 * <p>Las restricciones de obligatoriedad reproducen literalmente los campos exigidos por la
 * especificacion para cada tipo de registro.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    // ---------------------------------------------------------------- registro de cliente

    public record RegisterCustomerRequest(
            @NotBlank(message = "Los nombres son obligatorios")
            @Size(max = 80) String firstName,

            @NotBlank(message = "Los apellidos son obligatorios")
            @Size(max = 80) String lastName,

            @NotBlank(message = "El nombre de usuario es obligatorio")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{3,30}$",
                    message = "Usa entre 3 y 30 caracteres: letras, numeros, punto, guion o guion bajo")
            String username,

            @NotBlank(message = "La direccion es obligatoria")
            @Size(max = 200) String address,

            @NotBlank(message = "El codigo postal es obligatorio")
            @Size(max = 20) String postalCode,

            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato valido") String email,

            @NotBlank(message = "El telefono es obligatorio")
            @Size(max = 30) String phone,

            @NotBlank(message = "La contrasena es obligatoria")
            @Size(min = 8, max = 100, message = "La contrasena debe tener al menos 8 caracteres")
            String password,

            /** Campo opcional segun la especificacion. */
            String gender) {
    }

    // ---------------------------------------------------------------- registro de empresa

    public record CompanyExtraUser(
            @NotBlank(message = "El nombre del usuario adicional es obligatorio") String name,
            @NotBlank(message = "El correo del usuario adicional es obligatorio")
            @Email(message = "El correo no tiene un formato valido") String email) {
    }

    public record RegisterCompanyRequest(
            @NotBlank(message = "El nombre de la empresa es obligatorio")
            @Size(max = 150) String companyName,

            @NotBlank(message = "El nombre del representante legal es obligatorio")
            @Size(max = 150) String legalRepresentative,

            @NotBlank(message = "El NIT es obligatorio")
            @Size(max = 40) String nit,

            @NotBlank(message = "La direccion es obligatoria")
            @Size(max = 200) String address,

            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato valido") String email,

            @NotBlank(message = "El codigo postal es obligatorio")
            @Size(max = 20) String postalCode,

            @NotBlank(message = "La descripcion de la empresa es obligatoria")
            @Size(max = 2000) String description,

            @NotBlank(message = "El telefono es obligatorio")
            @Size(max = 30) String phone,

            @NotBlank(message = "La contrasena es obligatoria")
            @Size(min = 8, max = 100, message = "La contrasena debe tener al menos 8 caracteres")
            String password,

            /** Etiquetas de categorias de productos. Opcional. */
            List<String> categoryTags,

            /** Usuarios adicionales de la empresa. Opcional. */
            @Valid List<CompanyExtraUser> additionalUsers) {
    }

    // ---------------------------------------------------------------- inicio de sesion

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato valido") String email,
            @NotBlank(message = "La contrasena es obligatoria") String password) {
    }

    public record TwoFactorLoginRequest(
            @NotBlank(message = "Falta el token de la sesion en curso") String challengeToken,
            @NotBlank(message = "El codigo de verificacion es obligatorio") String code) {
    }

    public record RefreshRequest(
            @NotBlank(message = "Falta el token de renovacion") String refreshToken) {
    }

    /**
     * Respuesta del login.
     *
     * @param twoFactorRequired  cuando es verdadero solo llega {@code challengeToken} y falta el codigo
     * @param twoFactorReminder  la 2FA esta desactivada: el frontend muestra el recordatorio. En las
     *                           cuentas de empresa solo se marca para el usuario root
     */
    /**
     * Respuesta de sesion.
     *
     * <p>{@code accessToken} y {@code refreshToken} son de uso <b>interno</b>: el controlador los
     * toma para escribirlos en cookies {@code httpOnly} y despues llama a {@link #withoutTokens()},
     * de modo que <b>nunca lleguen al navegador en el cuerpo JSON</b>. Si llegaran, el codigo de la
     * pagina podria leerlos y un XSS robarlos, que es justo lo que la cookie httpOnly evita.
     *
     * <p>{@code challengeToken} si viaja en el cuerpo: no es una sesion, solo habilita el segundo
     * paso de la 2FA y caduca en minutos.
     */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            boolean twoFactorRequired,
            String challengeToken,
            boolean twoFactorReminder,
            UserSummary user) {

        public static AuthResponse challenge(String challengeToken) {
            return new AuthResponse(null, null, 0, true, challengeToken, false, null);
        }

        /** Copia sin los tokens, que es lo unico que se serializa hacia el navegador. */
        public AuthResponse withoutTokens() {
            return new AuthResponse(null, null, expiresInSeconds, twoFactorRequired, challengeToken,
                    twoFactorReminder, user);
        }
    }

    public record UserSummary(
            String id,
            String email,
            String type,
            String displayName,
            String companyId,
            String companyName,
            boolean root,
            boolean twoFactorEnabled,
            List<String> permissions) {
    }

    // ---------------------------------------------------------------- contrasena

    public record ForgotPasswordRequest(
            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato valido") String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Falta el token") String token,
            @NotBlank(message = "La contrasena es obligatoria")
            @Size(min = 8, max = 100, message = "La contrasena debe tener al menos 8 caracteres")
            String password) {
    }

    /** Respuesta generica para acciones sin cuerpo relevante. */
    public record MessageResponse(String message) {
    }
}
