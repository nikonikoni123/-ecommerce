package com.ecommerce.auth.token;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Refresh token persistido para poder revocarlo. Se guarda el hash, nunca el valor en claro,
 * de modo que una filtracion de la coleccion no permita suplantar sesiones.
 */
@Document("refreshTokens")
public class RefreshToken {

    @Id
    private String id;

    private String userId;
    private String tokenHash;
    private Instant expiresAt;
    private boolean revoked;
    private Instant createdAt = Instant.now();

    public RefreshToken() {
    }

    public RefreshToken(String userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(Instant.now());
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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
