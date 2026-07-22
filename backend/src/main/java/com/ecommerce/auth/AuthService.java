package com.ecommerce.auth;

import com.ecommerce.auth.dto.AuthDtos.AuthResponse;
import com.ecommerce.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.ecommerce.auth.dto.AuthDtos.LoginRequest;
import com.ecommerce.auth.dto.AuthDtos.RegisterCompanyRequest;
import com.ecommerce.auth.dto.AuthDtos.RegisterCustomerRequest;
import com.ecommerce.auth.dto.AuthDtos.ResetPasswordRequest;
import com.ecommerce.auth.dto.AuthDtos.TwoFactorLoginRequest;
import com.ecommerce.auth.dto.AuthDtos.UserSummary;
import com.ecommerce.auth.token.RefreshToken;
import com.ecommerce.auth.token.RefreshTokenRepository;
import com.ecommerce.auth.token.VerificationToken;
import com.ecommerce.auth.token.VerificationTokenRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.company.Company;
import com.ecommerce.company.CompanyRepository;
import com.ecommerce.company.Role;
import com.ecommerce.company.RoleRepository;
import com.ecommerce.mail.MailService;
import com.ecommerce.notification.Notification;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.security.JwtService;
import com.ecommerce.security.Permission;
import com.ecommerce.security.PermissionResolver;
import com.ecommerce.security.TotpService;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserStatus;
import com.ecommerce.user.UserType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Duration EMAIL_VERIFY_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final String TOTP_ISSUER = "E-Commerce";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final MailService mailService;
    private final PermissionResolver permissionResolver;
    private final NotificationService notificationService;

    public AuthService(UserRepository userRepository, CompanyRepository companyRepository,
                       RoleRepository roleRepository,
                       VerificationTokenRepository verificationTokenRepository,
                       RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TotpService totpService, MailService mailService,
                       PermissionResolver permissionResolver,
                       NotificationService notificationService) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.mailService = mailService;
        this.permissionResolver = permissionResolver;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ registro

    public void registerCustomer(RegisterCustomerRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese correo.");
        }
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw ApiException.conflict("USERNAME_TAKEN", "Ese nombre de usuario ya esta en uso.");
        }

        var user = new User();
        user.setType(UserType.CUSTOMER);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setUsername(request.username().trim());
        user.setAddress(request.address().trim());
        user.setPostalCode(request.postalCode().trim());
        user.setPhone(request.phone().trim());
        user.setGender(request.gender() == null || request.gender().isBlank()
                ? null : request.gender().trim());
        userRepository.save(user);

        sendEmailVerification(user);
    }

    public void registerCompany(RegisterCompanyRequest request) {
        String email = request.email().trim().toLowerCase();
        if (companyRepository.existsByNit(request.nit().trim())) {
            throw ApiException.conflict("NIT_TAKEN", "Ya existe una empresa registrada con ese NIT.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese correo.");
        }

        var company = new Company();
        company.setName(request.companyName().trim());
        company.setLegalRepresentative(request.legalRepresentative().trim());
        company.setNit(request.nit().trim());
        company.setAddress(request.address().trim());
        company.setPostalCode(request.postalCode().trim());
        company.setEmail(email);
        company.setPhone(request.phone().trim());
        company.setDescription(request.description().trim());
        company.setCategoryTags(normalizeTags(request.categoryTags()));
        company = companyRepository.save(company);

        // El primer usuario de la empresa es siempre el root, y es unico por empresa.
        var root = new User();
        root.setType(UserType.COMPANY_MEMBER);
        root.setEmail(email);
        root.setPasswordHash(passwordEncoder.encode(request.password()));
        root.setFirstName(request.legalRepresentative().trim());
        root.setAddress(request.address().trim());
        root.setPostalCode(request.postalCode().trim());
        root.setPhone(request.phone().trim());
        root.setCompanyId(company.getId());
        root.setRoot(true);
        root.setPosition("Representante legal");
        root = userRepository.save(root);

        company.setRootUserId(root.getId());
        companyRepository.save(company);

        createDefaultRoles(company.getId());
        inviteAdditionalUsers(request, company);

        sendEmailVerification(root);
    }

    /** Cargos base para que root tenga plantillas utiles desde el primer dia. */
    private void createDefaultRoles(String companyId) {
        List<Role> defaults = List.of(
                buildRole(companyId, "Administrador",
                        "Acceso completo a la gestion, salvo la propiedad de la cuenta root",
                        EnumSet.complementOf(EnumSet.of(Permission.COMPANY_SETTINGS))),
                buildRole(companyId, "Gestor de productos",
                        "Gestiona el catalogo y el inventario",
                        EnumSet.of(Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE,
                                Permission.PRODUCT_UPDATE, Permission.PRODUCT_DELETE,
                                Permission.STOCK_UPDATE, Permission.KPI_VIEW_OWN)),
                buildRole(companyId, "Agente de soporte",
                        "Atiende los casos de los usuarios y consulta pedidos",
                        EnumSet.of(Permission.CASE_VIEW, Permission.CASE_REPLY,
                                Permission.ORDER_VIEW, Permission.KPI_VIEW_OWN)),
                buildRole(companyId, "Consulta",
                        "Solo lectura del catalogo y de sus propios indicadores",
                        EnumSet.of(Permission.PRODUCT_VIEW, Permission.KPI_VIEW_OWN)));
        roleRepository.saveAll(defaults);
    }

    private Role buildRole(String companyId, String name, String description,
                           Set<Permission> permissions) {
        var role = new Role();
        role.setCompanyId(companyId);
        role.setName(name);
        role.setDescription(description);
        role.setPermissions(permissions);
        role.setSystem(true);
        return role;
    }

    /**
     * Los usuarios adicionales se crean sin contrasena y reciben un enlace para establecerla:
     * asi root nunca conoce la clave de sus empleados.
     */
    private void inviteAdditionalUsers(RegisterCompanyRequest request, Company company) {
        if (request.additionalUsers() == null || request.additionalUsers().isEmpty()) {
            return;
        }
        var viewerRole = roleRepository.findByCompanyIdAndNameIgnoreCase(company.getId(), "Consulta");

        for (var extra : request.additionalUsers()) {
            String extraEmail = extra.email().trim().toLowerCase();
            if (userRepository.existsByEmailIgnoreCase(extraEmail)) {
                log.warn("Se omite el usuario adicional {}: el correo ya esta registrado", extraEmail);
                continue;
            }
            var member = new User();
            member.setType(UserType.COMPANY_MEMBER);
            member.setEmail(extraEmail);
            // Sin contrasena utilizable hasta que la establezca desde el enlace del correo.
            member.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            member.setFirstName(extra.name().trim());
            member.setCompanyId(company.getId());
            member.setPhone(company.getPhone());
            member.setAddress(company.getAddress());
            member.setPostalCode(company.getPostalCode());
            viewerRole.ifPresent(role -> member.setRoleIds(Set.of(role.getId())));
            userRepository.save(member);

            String token = createToken(member, VerificationToken.Purpose.PASSWORD_RESET,
                    PASSWORD_RESET_TTL);
            mailService.sendPasswordReset(member.getEmail(), member.getFirstName(), token);
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase())
                .distinct()
                .toList();
    }

    // ------------------------------------------------------------------ verificacion de correo

    private void sendEmailVerification(User user) {
        String token = createToken(user, VerificationToken.Purpose.EMAIL_VERIFY, EMAIL_VERIFY_TTL);
        mailService.sendEmailVerification(user.getEmail(), user.displayName().trim(), token);
    }

    private String createToken(User user, VerificationToken.Purpose purpose, Duration ttl) {
        verificationTokenRepository.deleteByUserIdAndPurpose(user.getId(), purpose);
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        verificationTokenRepository.save(
                new VerificationToken(user.getId(), token, purpose, Instant.now().plus(ttl)));
        return token;
    }

    public void verifyEmail(String token) {
        var verification = verificationTokenRepository.findByToken(token)
                .filter(t -> t.getPurpose() == VerificationToken.Purpose.EMAIL_VERIFY)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN",
                        "El enlace de verificacion no es valido."));
        if (!verification.isUsable()) {
            throw ApiException.badRequest("EXPIRED_TOKEN",
                    "El enlace de verificacion caduco. Solicita uno nuevo.");
        }

        var user = userRepository.findById(verification.getUserId())
                .orElseThrow(() -> ApiException.notFound("La cuenta ya no existe."));
        user.setEmailVerified(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        verification.setUsedAt(Instant.now());
        verificationTokenRepository.save(verification);

        // La empresa queda verificada cuando su usuario root confirma el correo.
        if (user.isRoot() && user.getCompanyId() != null) {
            companyRepository.findById(user.getCompanyId()).ifPresent(company -> {
                company.setVerified(true);
                companyRepository.save(company);
            });
        }

        mailService.sendWelcome(user.getEmail(), user.displayName().trim());
        notificationService.push(user.getId(), Notification.Type.ACCOUNT,
                "Tu cuenta esta activa",
                "Confirmamos tu correo electronico. Ya puedes usar la plataforma.", "/cuenta");
    }

    public void resendVerification(String email) {
        userRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendEmailVerification);
        // Se responde siempre igual, para no revelar que correos estan registrados.
    }

    // ------------------------------------------------------------------ inicio de sesion

    public AuthResponse login(LoginRequest request) {
        var user = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("BAD_CREDENTIALS",
                        "El correo o la contrasena no son correctos."));

        if (user.getStatus() == UserStatus.DELETED) {
            throw ApiException.unauthorized("ACCOUNT_DELETED", "Esta cuenta fue dada de baja.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Esta cuenta esta suspendida.");
        }
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED",
                    "Debes confirmar tu correo antes de iniciar sesion.");
        }

        if (user.isTwoFactorEnabled()) {
            return AuthResponse.challenge(jwtService.issueTwoFactorChallengeToken(user.getId()));
        }
        return issueSession(user);
    }

    public AuthResponse loginTwoFactor(TwoFactorLoginRequest request) {
        var claims = jwtService.parse(request.challengeToken())
                .filter(jwtService::isTwoFactorChallenge)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CHALLENGE",
                        "La sesion de verificacion caduco. Inicia sesion de nuevo."));

        var user = userRepository.findById(claims.getSubject())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CHALLENGE",
                        "La sesion de verificacion no es valida."));

        if (!totpService.verify(user.getTwoFactorSecret(), request.code())) {
            throw ApiException.unauthorized("INVALID_2FA_CODE",
                    "El codigo de verificacion no es correcto.");
        }
        return issueSession(user);
    }

    private AuthResponse issueSession(User user) {
        String accessToken = jwtService.issueAccessToken(
                user.getId(), user.getEmail(), user.getType().name());

        String rawRefresh = jwtService.generateRefreshToken();
        refreshTokenRepository.save(new RefreshToken(
                user.getId(), jwtService.hashRefreshToken(rawRefresh), jwtService.refreshExpiry()));

        return new AuthResponse(
                accessToken,
                rawRefresh,
                jwtService.accessTtlSeconds(),
                false,
                null,
                twoFactorReminderFor(user),
                toSummary(user));
    }

    /**
     * La 2FA viene desactivada por defecto y se recuerda en cada ingreso. En las cuentas de empresa
     * el recordatorio corresponde unicamente al usuario root.
     */
    private boolean twoFactorReminderFor(User user) {
        if (user.isTwoFactorEnabled()) {
            return false;
        }
        return user.getType() != UserType.COMPANY_MEMBER || user.isRoot();
    }

    public UserSummary toSummary(User user) {
        String companyName = Optional.ofNullable(user.getCompanyId())
                .flatMap(companyRepository::findById)
                .map(Company::getName)
                .orElse(null);

        List<String> permissions = new ArrayList<>(
                permissionResolver.resolve(user).stream().map(Enum::name).sorted().toList());

        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getType().name(),
                user.displayName().trim(),
                user.getCompanyId(),
                companyName,
                user.isRoot(),
                user.isTwoFactorEnabled(),
                permissions);
    }

    // ------------------------------------------------------------------ renovacion y cierre

    public AuthResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        var stored = refreshTokenRepository.findByTokenHash(hash)
                .filter(RefreshToken::isUsable)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH",
                        "Tu sesion caduco. Inicia sesion de nuevo."));

        var user = userRepository.findById(stored.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH",
                        "Tu sesion ya no es valida."));

        // Rotacion: el token usado se invalida y se emite uno nuevo.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueSession(user);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    // ------------------------------------------------------------------ contrasena

    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    String token = createToken(user, VerificationToken.Purpose.PASSWORD_RESET,
                            PASSWORD_RESET_TTL);
                    mailService.sendPasswordReset(user.getEmail(), user.displayName().trim(), token);
                });
        // Respuesta identica exista o no la cuenta, para no filtrar correos registrados.
    }

    public void resetPassword(ResetPasswordRequest request) {
        var verification = verificationTokenRepository.findByToken(request.token())
                .filter(t -> t.getPurpose() == VerificationToken.Purpose.PASSWORD_RESET)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN",
                        "El enlace no es valido."));
        if (!verification.isUsable()) {
            throw ApiException.badRequest("EXPIRED_TOKEN",
                    "El enlace caduco. Solicita uno nuevo.");
        }

        var user = userRepository.findById(verification.getUserId())
                .orElseThrow(() -> ApiException.notFound("La cuenta ya no existe."));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Establecer la contrasena desde el enlace del correo demuestra el control de la cuenta.
        user.setEmailVerified(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        verification.setUsedAt(Instant.now());
        verificationTokenRepository.save(verification);

        // Cerrar todas las sesiones abiertas tras un cambio de contrasena.
        refreshTokenRepository.deleteByUserId(user.getId());

        mailService.sendNotice(user.getEmail(), user.displayName().trim(),
                "Tu contrasena fue actualizada", "Tu contrasena fue actualizada",
                "Si no fuiste tu, contacta con soporte de inmediato.");
        notificationService.push(user.getId(), Notification.Type.SECURITY,
                "Contrasena actualizada",
                "Se cambio la contrasena de tu cuenta y se cerraron las demas sesiones.", "/cuenta");
    }

    public String totpIssuer() {
        return TOTP_ISSUER;
    }
}
