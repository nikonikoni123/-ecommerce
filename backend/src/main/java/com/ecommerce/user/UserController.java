package com.ecommerce.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.auth.dto.AuthDtos.MessageResponse;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.user.dto.UserDtos.ChangePasswordRequest;
import com.ecommerce.user.dto.UserDtos.DeleteAccountRequest;
import com.ecommerce.user.dto.UserDtos.ProfileResponse;
import com.ecommerce.user.dto.UserDtos.TwoFactorCodeRequest;
import com.ecommerce.user.dto.UserDtos.TwoFactorSetupResponse;
import com.ecommerce.user.dto.UserDtos.UpdateProfileRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Mi cuenta", description = "Datos del usuario, contrasena, 2FA y baja de la cuenta")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Consultar el perfil del usuario autenticado")
    public ProfileResponse profile(@AuthenticationPrincipal AppPrincipal principal) {
        return userService.profile(principal);
    }

    @PatchMapping
    @Operation(summary = "Modificar los datos del usuario")
    public ProfileResponse updateProfile(@AuthenticationPrincipal AppPrincipal principal,
                                         @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal, request);
    }

    @PatchMapping("/2fa/toggle")
    @Operation(summary = "Activar o desactivar 2FA directamente")
    public ProfileResponse toggle2fa(@AuthenticationPrincipal AppPrincipal principal, 
                                    @RequestParam boolean enable) {
        return userService.toggleTwoFactor(principal, enable);
    }

    @PostMapping("/password")
    @Operation(summary = "Cambiar la contrasena y cerrar las demas sesiones")
    public MessageResponse changePassword(@AuthenticationPrincipal AppPrincipal principal,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal, request);
        return new MessageResponse("Contrasena actualizada.");
    }

    @PostMapping("/2fa/setup")
    @Operation(summary = "Generar el secreto TOTP y la URI para el codigo QR")
    public TwoFactorSetupResponse setupTwoFactor(@AuthenticationPrincipal AppPrincipal principal) {
        return userService.startTwoFactorSetup(principal);
    }

    @PostMapping("/2fa/enable")
    @Operation(summary = "Activar la verificacion en dos pasos confirmando un codigo valido")
    public MessageResponse enableTwoFactor(@AuthenticationPrincipal AppPrincipal principal,
                                           @Valid @RequestBody TwoFactorCodeRequest request) {
        userService.enableTwoFactor(principal, request.code());
        return new MessageResponse("Verificacion en dos pasos activada.");
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Desactivar la verificacion en dos pasos")
    public MessageResponse disableTwoFactor(@AuthenticationPrincipal AppPrincipal principal,
                                            @Valid @RequestBody TwoFactorCodeRequest request) {
        userService.disableTwoFactor(principal, request.code());
        return new MessageResponse("Verificacion en dos pasos desactivada.");
    }

    @DeleteMapping
    @Operation(summary = "Eliminar la cuenta. Borrado logico con anonimizacion de los datos")
    public MessageResponse deleteAccount(@AuthenticationPrincipal AppPrincipal principal,
                                         @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(principal, request);
        return new MessageResponse("Tu cuenta fue eliminada.");
    }
}
