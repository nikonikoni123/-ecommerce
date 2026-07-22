package com.ecommerce.auth.token;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Token de un solo uso para verificar el correo o restablecer la contrasena.
 * Mongo lo elimina solo al vencer, mediante el indice TTL sobre {@code expiresAt}.
 */
@Document("verificationTokens")
public class VerificationToken {

    public enum Purpose {
        EMAIL_VERIFY,
        PASSWORD_RESET
    }

    @Id
    private String id;

    private String userId;
    private String token;
    private Purpose purpose;
    private Instant expiresAt;
    private Instant usedAt;

    public VerificationToken() {
    }

    public VerificationToken(String userId, String token, Purpose purpose, Instant expiresAt) {
        this.userId = userId;
        this.token = token;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public void setPurpose(Purpose purpose) {
        this.purpose = purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
