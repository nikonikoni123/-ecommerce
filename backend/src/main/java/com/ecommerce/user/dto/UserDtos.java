package com.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    /** Perfil completo del usuario autenticado. */
    public record ProfileResponse(
            String id,
            String type,
            String email,
            String firstName,
            String lastName,
            String username,
            String address,
            String postalCode,
            String phone,
            String gender,
            boolean emailVerified,
            boolean twoFactorEnabled,
            boolean twoFactorReminder,
            String companyId,
            String companyName,
            boolean root,
            String position,
            java.util.List<String> permissions) {
    }

    /**
     * Modificacion del perfil. Todos los campos son opcionales: solo se aplican los presentes,
     * y los que llegan no pueden quedar vacios.
     */
    public record UpdateProfileRequest(
            @Size(min = 1, max = 80, message = "Los nombres no pueden quedar vacios") String firstName,
            @Size(min = 1, max = 80, message = "Los apellidos no pueden quedar vacios") String lastName,
            @Size(min = 1, max = 200, message = "La direccion no puede quedar vacia") String address,
            @Size(min = 1, max = 20, message = "El codigo postal no puede quedar vacio") String postalCode,
            @Size(min = 1, max = 30, message = "El telefono no puede quedar vacio") String phone,
            String gender) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Indica tu contrasena actual") String currentPassword,
            @NotBlank(message = "La contrasena nueva es obligatoria")
            @Size(min = 8, max = 100, message = "La contrasena debe tener al menos 8 caracteres")
            String newPassword) {
    }

    /** Datos que el frontend necesita para pintar el QR de la verificacion en dos pasos. */
    public record TwoFactorSetupResponse(String secret, String otpAuthUri) {
    }

    public record TwoFactorCodeRequest(
            @NotBlank(message = "El codigo de verificacion es obligatorio") String code) {
    }

    /** La baja de la cuenta exige la contrasena, para que no baste con robar una sesion abierta. */
    public record DeleteAccountRequest(
            @NotBlank(message = "Confirma tu contrasena para eliminar la cuenta") String password) {
    }
}
