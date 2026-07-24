package com.ecommerce.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Emite y retira la sesion en cookies {@code httpOnly}.
 *
 * <p>El motivo de que la sesion viaje en cookie y no en el cuerpo de la respuesta es que
 * {@code httpOnly} la hace <b>ilegible desde JavaScript</b>: si la aplicacion sufriera un XSS, el
 * atacante no podria leer ni exfiltrar el token. Guardarlo en {@code localStorage} lo dejaba al
 * alcance de cualquier script de la pagina.
 *
 * <p>La cookie de refresco se limita a {@code /api/auth}: solo se envia a los endpoints que la
 * necesitan (renovar y cerrar sesion), en vez de acompañar a todas las peticiones de la API.
 *
 * <p>{@code Secure} se configura porque en desarrollo se sirve por HTTP, donde el navegador
 * descartaria una cookie marcada como segura; en produccion debe ir activado.
 */
@Service
public class SessionCookieService {

    public static final String ACCESS_COOKIE = "ecommerce_access";
    public static final String REFRESH_COOKIE = "ecommerce_refresh";

    /** La cookie de refresco no viaja fuera de los endpoints de autenticacion. */
    private static final String REFRESH_PATH = "/api/auth";
    private static final String ROOT_PATH = "/";

    private final JwtService jwtService;
    private final boolean secure;
    private final String sameSite;

    public SessionCookieService(JwtService jwtService,
                                @Value("${app.cookies.secure:false}") boolean secure,
                                @Value("${app.cookies.same-site:Lax}") String sameSite) {
        this.jwtService = jwtService;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /** Escribe la sesion recien emitida en las dos cookies. */
    public void write(HttpServletResponse response, String accessToken, String refreshToken) {
        long refreshSeconds = Math.max(0,
                Duration.between(Instant.now(), jwtService.refreshExpiry()).getSeconds());

        response.addHeader(HttpHeaders.SET_COOKIE,
                build(ACCESS_COOKIE, accessToken, ROOT_PATH, jwtService.accessTtlSeconds()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                build(REFRESH_COOKIE, refreshToken, REFRESH_PATH, refreshSeconds).toString());
    }

    /** Retira ambas cookies al cerrar sesion, con la misma ruta con la que se crearon. */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(ACCESS_COOKIE, "", ROOT_PATH, 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, build(REFRESH_COOKIE, "", REFRESH_PATH, 0).toString());
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return read(request, ACCESS_COOKIE);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return read(request, REFRESH_COOKIE);
    }

    private Optional<String> read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private ResponseCookie build(String name, String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
