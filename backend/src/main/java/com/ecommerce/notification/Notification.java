package com.ecommerce.notification;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notifications")
public class Notification {

    public enum Type {
        ACCOUNT,
        SECURITY,
        ORDER,
        PRODUCT,
        CASE,
        BROADCAST
    }

    @Id
    private String id;

    private String recipientId;
    private Type type;
    private String title;
    private String body;

    /** Ruta del frontend a la que lleva la notificacion al pulsarla. Opcional. */
    private String link;

    private boolean read;

    private Map<String, String> metadata = new HashMap<>();

    private Instant createdAt = Instant.now();

    public Notification() {
    }

    public Notification(String recipientId, Type type, String title, String body, String link) {
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
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
