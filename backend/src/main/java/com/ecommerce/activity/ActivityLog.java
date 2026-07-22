package com.ecommerce.activity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de actividad de los miembros de una empresa. Es la base de la pantalla en la que root
 * "visualiza la actividad de cada usuario de su empresa".
 */
@Document("activityLog")
public class ActivityLog {

    @Id
    private String id;

    private String companyId;
    private String userId;
    private String userEmail;

    /** Accion en formato VERBO_RECURSO, por ejemplo {@code PRODUCT_CREATE}. */
    private String action;

    private String targetType;
    private String targetId;

    private Map<String, String> metadata = new HashMap<>();

    private Instant createdAt = Instant.now();

    public ActivityLog() {
    }

    public ActivityLog(String companyId, String userId, String userEmail, String action,
                       String targetType, String targetId) {
        this.companyId = companyId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata == null ? new HashMap<>() : metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
