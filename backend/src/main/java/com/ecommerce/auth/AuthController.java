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
import com.ecommerce.auth.dto.AuthDtos.UserSummary;
import com.ecommerce.common.ApiException;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.SessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final SessionCookieService cookieService;

    public AuthController(AuthService authService, SessionCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
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
    @Operation(summary = "Iniciar sesion. Si la 2FA esta activa devuelve un reto en lugar de la sesion")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletResponse response) {
        return establish(authService.login(request), response);
    }

    @PostMapping("/login/2fa")
    @Operation(summary = "Completar el inicio de sesion con el codigo de verificacion en dos pasos")
    public AuthResponse loginTwoFactor(@Valid @RequestBody TwoFactorLoginRequest request,
                                       HttpServletResponse response) {
        return establish(authService.loginTwoFactor(request), response);
    }

    @GetMapping("/session")
    @Operation(summary = "Usuario de la sesion actual, tomada de la cookie")
    public UserSummary session(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "No hay una sesion activa.");
        }
        return authService.sessionOf(principal.userId());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el acceso rotando el token de renovacion que viaja en la cookie")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response,
                                @RequestBody(required = false) RefreshRequest body) {
        return establish(authService.refresh(refreshTokenOf(request, body)), response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar la sesion, invalidando el token de renovacion y borrando las cookies")
    public MessageResponse logout(HttpServletRequest request, HttpServletResponse response,
                                  @RequestBody(required = false) RefreshRequest body) {
        var token = refreshTokenOf(request, body);
        if (token != null) {
            authService.logout(token);
        }
        // Las cookies se retiran siempre, aunque el token ya no fuera valido.
        cookieService.clear(response);
        return new MessageResponse("Sesion cerrada.");
    }

    /**
     * Escribe la sesion en cookies httpOnly y devuelve la respuesta <b>sin los tokens</b>, para que
     * no queden al alcance del JavaScript de la pagina.
     */
    private AuthResponse establish(AuthResponse session, HttpServletResponse response) {
        if (session.accessToken() != null) {
            cookieService.write(response, session.accessToken(), session.refreshToken());
        }
        return session.withoutTokens();
    }

    /**
     * El token de renovacion se toma de la cookie. Se admite el cuerpo como alternativa para los
     * clientes que no son el navegador (Swagger, scripts), que no manejan cookies.
     */
    private String refreshTokenOf(HttpServletRequest request, RefreshRequest body) {
        return cookieService.readRefreshToken(request)
                .orElseGet(() -> body == null ? null : body.refreshToken());
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
