package com.ecommerce.chat;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Mensaje del chat general de una empresa.
 *
 * <p>El chat general lo lee todo el personal, pero solo publican quienes root autoriza. Los
 * comunicados de root a toda la empresa se guardan aqui mismo con tipo {@code BROADCAST}, para que
 * aparezcan en el mismo hilo destacados, en lugar de vivir en otra coleccion.
 *
 * <p>Los chats por departamento o equipo llegan en la Fase 5, cuando existan los departamentos a
 * los que pertenecen; el campo {@code channel} deja sitio para ellos sin migrar nada.
 */
@Document("chatMessages")
public class ChatMessage {

    public enum Type {
        /** Mensaje normal del chat general. */
        MESSAGE,
        /** Comunicado de root a toda la empresa. */
        BROADCAST
    }

    @Id
    private String id;

    private String companyId;

    /** Canal del mensaje. Por ahora siempre "general"; los de equipo son de la Fase 5. */
    private String channel = "general";

    private Type type = Type.MESSAGE;

    private String authorId;
    private String authorName;
    private String body;

    private Instant createdAt = Instant.now();

    public ChatMessage() {
    }

    public ChatMessage(String companyId, Type type, String authorId, String authorName, String body) {
        this.companyId = companyId;
        this.type = type;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
