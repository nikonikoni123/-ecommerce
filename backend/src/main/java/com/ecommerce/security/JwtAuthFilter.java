package com.ecommerce.security;

import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traduce el JWT del encabezado Authorization en una autenticacion de Spring Security.
 *
 * <p>Recarga el usuario en cada peticion para recalcular sus permisos: asi un cambio de cargo
 * aplica de inmediato, y una cuenta dada de baja o suspendida deja de tener acceso sin esperar a
 * que caduque su token.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PermissionResolver permissionResolver;
    private final SessionCookieService cookieService;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository,
                         PermissionResolver permissionResolver,
                         SessionCookieService cookieService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.permissionResolver = permissionResolver;
        this.cookieService = cookieService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        var token = tokenOf(request);
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        jwtService.parse(token.get())
                // Un token de reto 2FA no autentica: solo sirve para completar el segundo factor.
                .filter(claims -> !jwtService.isTwoFactorChallenge(claims))
                .map(claims -> claims.getSubject())
                .flatMap(userRepository::findById)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(this::authenticate);

        chain.doFilter(request, response);
    }

    /**
     * El token se toma de la cookie httpOnly, que es como viaja desde el navegador. Se sigue
     * admitiendo {@code Authorization: Bearer} para los clientes que no son el navegador —Swagger,
     * scripts y pruebas—, lo que no debilita nada: un XSS no puede leer la cookie ni, por tanto,
     * construir ese encabezado.
     */
    private java.util.Optional<String> tokenOf(HttpServletRequest request) {
        var fromCookie = cookieService.readAccessToken(request);
        if (fromCookie.isPresent()) {
            return fromCookie;
        }
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith(BEARER_PREFIX)
                ? java.util.Optional.of(header.substring(BEARER_PREFIX.length()))
                : java.util.Optional.empty();
    }

    private void authenticate(User user) {
        var permissions = permissionResolver.resolve(user);
        var principal = new AppPrincipal(
                user.getId(), user.getEmail(), user.getType(), user.getCompanyId(),
                user.isRoot(), permissions);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getType().name()));
        if (user.isRoot()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ROOT"));
        }
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
