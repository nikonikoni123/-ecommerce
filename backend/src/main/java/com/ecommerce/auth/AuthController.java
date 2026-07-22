package com.ecommerce.auth;

import com.ecommerce.auth.dto.AuthDtos.AuthResponse;
import com.ecommerce.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.ecommerce.auth.dto.AuthDtos.LoginRequest;
import com.ecommerce.auth.dto.AuthDtos.MessageResponse;
import com.ecommerce.auth.dto.AuthDtos.RefreshRequest;
import com.ecommerce.auth.dto.AuthDtos.RegisterCompanyRequest;
import com.ecommerce.auth.dto.AuthDtos.RegisterCustomerRequest;
import com.ecommerce.auth.dto.AuthDtos.ResetPasswordRequest;
import com.ecommerce.auth.dto.AuthDtos.TwoFactorLoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacion", description = "Registro, verificacion de correo, sesion y contrasena")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/customer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un usuario comprador y enviarle el correo de verificacion")
    public MessageResponse registerCustomer(@Valid @RequestBody RegisterCustomerRequest request) {
        authService.registerCustomer(request);
        return new MessageResponse(
                "Cuenta creada. Revisa tu correo para confirmar la direccion.");
    }

    @PostMapping("/register/company")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una empresa junto con su unico usuario root")
    public MessageResponse registerCompany(@Valid @RequestBody RegisterCompanyRequest request) {
        authService.registerCompany(request);
        return new MessageResponse(
                "Empresa creada. Revisa el correo del representante legal para confirmar la cuenta.");
    }

    @PostMapping("/verify")
    @Operation(summary = "Confirmar la direccion de correo con el token recibido")
    public MessageResponse verify(@RequestParam String token) {
        authService.verifyEmail(token);
        return new MessageResponse("Correo confirmado. Ya puedes iniciar sesion.");
    }

    @PostMapping("/verify/resend")
    @Operation(summary = "Reenviar el correo de verificacion")
    public MessageResponse resendVerification(@RequestParam String email) {
        authService.resendVerification(email);
        return new MessageResponse(
                "Si el correo corresponde a una cuenta sin confirmar, te enviamos un enlace nuevo.");
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion. Si la 2FA esta activa devuelve un reto en lugar del token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/login/2fa")
    @Operation(summary = "Completar el inicio de sesion con el codigo de verificacion en dos pasos")
    public AuthResponse loginTwoFactor(@Valid @RequestBody TwoFactorLoginRequest request) {
        return authService.loginTwoFactor(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el token de acceso rotando el token de renovacion")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar la sesion invalidando el token de renovacion")
    public MessageResponse logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return new MessageResponse("Sesion cerrada.");
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Solicitar un enlace para restablecer la contrasena")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return new MessageResponse(
                "Si el correo corresponde a una cuenta, te enviamos un enlace para cambiar la contrasena.");
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Establecer una contrasena nueva con el token recibido por correo")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Contrasena actualizada. Ya puedes iniciar sesion.");
    }
}
