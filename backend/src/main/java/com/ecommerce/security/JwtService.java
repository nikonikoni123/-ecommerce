package com.ecommerce.security;

import com.ecommerce.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Emision y validacion de los JWT de acceso, y generacion de los refresh tokens opacos. */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    /**
     * Marca los tokens emitidos tras superar el segundo factor. Un token con 2FA pendiente no vale
     * para nada mas que completar el login.
     */
    private static final String CLAIM_PENDING_2FA = "pending2fa";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public JwtService(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(properties.jwt().accessExpirationMinutes());
        this.refreshTtl = Duration.ofDays(properties.jwt().refreshExpirationDays());
    }

    public String issueAccessToken(String userId, String email, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Token de vida muy corta emitido cuando las credenciales son correctas pero falta el codigo
     * TOTP. Solo lo acepta el endpoint que valida el segundo factor.
     */
    public String issueTwoFactorChallengeToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_PENDING_2FA, true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isTwoFactorChallenge(Claims claims) {
        return Boolean.TRUE.equals(claims.get(CLAIM_PENDING_2FA, Boolean.class));
    }

    /** Refresh token opaco de 256 bits. Del lado del servidor solo se guarda su hash. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public Instant refreshExpiry() {
        return Instant.now().plus(refreshTtl);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
