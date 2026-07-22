package com.ecommerce.user;

import com.ecommerce.auth.token.RefreshTokenRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.company.Company;
import com.ecommerce.company.CompanyRepository;
import com.ecommerce.mail.MailService;
import com.ecommerce.notification.Notification;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.PermissionResolver;
import com.ecommerce.security.TotpService;
import com.ecommerce.user.dto.UserDtos.ChangePasswordRequest;
import com.ecommerce.user.dto.UserDtos.DeleteAccountRequest;
import com.ecommerce.user.dto.UserDtos.ProfileResponse;
import com.ecommerce.user.dto.UserDtos.TwoFactorSetupResponse;
import com.ecommerce.user.dto.UserDtos.UpdateProfileRequest;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final String TOTP_ISSUER = "E-Commerce";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final PermissionResolver permissionResolver;
    private final MailService mailService;
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository, CompanyRepository companyRepository,
                       RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                       TotpService totpService, PermissionResolver permissionResolver,
                       MailService mailService, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.permissionResolver = permissionResolver;
        this.mailService = mailService;
        this.notificationService = notificationService;
    }

    public User require(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("La cuenta no existe."));
    }

    public ProfileResponse profile(AppPrincipal principal) {
        return toProfile(require(principal.userId()));
    }

    public ProfileResponse toProfile(User user) {
        String companyName = Optional.ofNullable(user.getCompanyId())
                .flatMap(companyRepository::findById)
                .map(Company::getName)
                .orElse(null);

        boolean reminder = !user.isTwoFactorEnabled()
                && (user.getType() != UserType.COMPANY_MEMBER || user.isRoot());

        return new ProfileResponse(
                user.getId(), user.getType().name(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getUsername(), user.getAddress(), user.getPostalCode(),
                user.getPhone(), user.getGender(), user.isEmailVerified(), user.isTwoFactorEnabled(),
                reminder, user.getCompanyId(), companyName, user.isRoot(), user.getPosition(),
                permissionResolver.resolve(user).stream().map(Enum::name).sorted().toList());
    }

    public ProfileResponse updateProfile(AppPrincipal principal, UpdateProfileRequest request) {
        var user = require(principal.userId());

        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        if (request.address() != null) {
            user.setAddress(request.address().trim());
        }
        if (request.postalCode() != null) {
            user.setPostalCode(request.postalCode().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().trim());
        }
        if (request.gender() != null) {
            // Una cadena vacia limpia el campo, que es opcional.
            user.setGender(request.gender().isBlank() ? null : request.gender().trim());
        }
        user.setUpdatedAt(Instant.now());
        return toProfile(userRepository.save(user));
    }

    public void changePassword(AppPrincipal principal, ChangePasswordRequest request) {
        var user = require(principal.userId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("BAD_PASSWORD", "La contrasena actual no es correcta.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Un cambio de contrasena invalida el resto de sesiones abiertas.
        refreshTokenRepository.deleteByUserId(user.getId());

        mailService.sendNotice(user.getEmail(), user.displayName().trim(),
                "Tu contrasena fue actualizada", "Tu contrasena fue actualizada",
                "Si no fuiste tu, restablece tu contrasena de inmediato.");
        notificationService.push(user.getId(), Notification.Type.SECURITY, "Contrasena actualizada",
                "Se cambio la contrasena de tu cuenta.", "/cuenta");
    }

    // ------------------------------------------------------------------ verificacion en dos pasos

    /**
     * Genera el secreto pero todavia no activa la 2FA: queda pendiente hasta que el usuario
     * demuestre que su aplicacion genera codigos validos.
     */
    public TwoFactorSetupResponse startTwoFactorSetup(AppPrincipal principal) {
        var user = require(principal.userId());
        if (user.isTwoFactorEnabled()) {
            throw ApiException.badRequest("2FA_ALREADY_ENABLED",
                    "La verificacion en dos pasos ya esta activa.");
        }

        String secret = totpService.generateSecret();
        user.setTwoFactorSecret(secret);
        user.setTwoFactorPending(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        return new TwoFactorSetupResponse(secret,
                totpService.buildOtpAuthUri(secret, user.getEmail(), TOTP_ISSUER));
    }

    public void enableTwoFactor(AppPrincipal principal, String code) {
        var user = require(principal.userId());
        if (user.getTwoFactorSecret() == null || !user.isTwoFactorPending()) {
            throw ApiException.badRequest("2FA_NOT_STARTED",
                    "Primero genera el codigo QR desde la configuracion de tu cuenta.");
        }
        if (!totpService.verify(user.getTwoFactorSecret(), code)) {
            throw ApiException.badRequest("INVALID_2FA_CODE",
                    "El codigo no es correcto. Revisa la hora de tu dispositivo e intenta de nuevo.");
        }

        user.setTwoFactorEnabled(true);
        user.setTwoFactorPending(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        mailService.sendNotice(user.getEmail(), user.displayName().trim(),
                "Verificacion en dos pasos activada", "Verificacion en dos pasos activada",
                "A partir de ahora te pediremos un codigo cada vez que inicies sesion.");
        notificationService.push(user.getId(), Notification.Type.SECURITY,
                "Verificacion en dos pasos activada",
                "Tu cuenta ahora pide un codigo adicional al iniciar sesion.", "/cuenta");
    }

    /** Desactivar exige un codigo valido: si no, una sesion robada podria quitar el segundo factor. */
    public void disableTwoFactor(AppPrincipal principal, String code) {
        var user = require(principal.userId());
        if (!user.isTwoFactorEnabled()) {
            throw ApiException.badRequest("2FA_NOT_ENABLED",
                    "La verificacion en dos pasos no esta activa.");
        }
        if (!totpService.verify(user.getTwoFactorSecret(), code)) {
            throw ApiException.badRequest("INVALID_2FA_CODE", "El codigo no es correcto.");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorPending(false);
        user.setTwoFactorSecret(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        mailService.sendNotice(user.getEmail(), user.displayName().trim(),
                "Verificacion en dos pasos desactivada", "Verificacion en dos pasos desactivada",
                "Si no fuiste tu, cambia tu contrasena y vuelve a activarla.");
        notificationService.push(user.getId(), Notification.Type.SECURITY,
                "Verificacion en dos pasos desactivada",
                "Tu cuenta ya no pide un codigo adicional al iniciar sesion.", "/cuenta");
    }

    // ------------------------------------------------------------------ baja de la cuenta

    /**
     * Borrado logico: los pedidos y las facturas ya emitidas deben seguir siendo trazables, asi que
     * el documento se conserva y se anonimizan los datos personales.
     */
    public void deleteAccount(AppPrincipal principal, DeleteAccountRequest request) {
        var user = require(principal.userId());
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("BAD_PASSWORD", "La contrasena no es correcta.");
        }
        if (user.isRoot()) {
            throw ApiException.badRequest("ROOT_CANNOT_DELETE",
                    "El usuario root no puede darse de baja: transfiere antes la propiedad de la empresa.");
        }

        String originalEmail = user.getEmail();
        String originalName = user.displayName().trim();

        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        // Se libera el correo y el usuario, y se borran los datos personales.
        user.setEmail("eliminado+" + user.getId() + "@ecommerce.local");
        user.setUsername(null);
        user.setPhone(null);
        user.setAddress(null);
        user.setPostalCode(null);
        user.setGender(null);
        user.setTwoFactorSecret(null);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);

        refreshTokenRepository.deleteByUserId(user.getId());

        mailService.sendNotice(originalEmail, originalName,
                "Tu cuenta fue eliminada", "Tu cuenta fue eliminada",
                "Lamentamos verte partir. Tus datos personales fueron borrados de la plataforma.");
    }
}
