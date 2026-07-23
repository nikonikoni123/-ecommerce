package com.ecommerce.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Caso de atencion: un hilo de conversacion entre un cliente y una empresa.
 *
 * <p>Es la pieza que une los dos requisitos de la especificacion: por el lado del cliente resuelve
 * "realizar comentarios o peticiones al vendedor"; por el de la empresa alimenta la "pestana de
 * atencion a casos" que el modelo BERT ordena por prioridad, fecha y vencimiento.
 *
 * <p>Los mensajes van embebidos porque siempre se leen junto al caso y no hace falta consultarlos
 * por separado. La clasificacion del BERT (prioridad, sentimiento) se guarda al abrir el caso: es
 * una foto del momento, y recalcularla en cada listado seria caro y volatil.
 */
@Document("supportCases")
public class SupportCase {

    @Id
    private String id;

    /** Numero legible, del estilo CASO-2026-000123. */
    private String number;

    private String companyId;
    private String companyName;

    private String customerId;
    private String customerEmail;
    private String customerName;

    /** Pedido al que se refiere el caso. Opcional: hay casos generales. */
    private String orderId;
    private String orderNumber;

    private String subject;

    private CaseStatus status = CaseStatus.ABIERTO;

    // --- Clasificacion del BERT, tomada al abrir el caso ---
    private String priority;    // HIGH, MEDIUM, LOW
    private String sentiment;   // NEGATIVE, NEUTRAL, POSITIVE
    private String aiReason;

    /** El resultado vino del modelo BERT; si es falso, de la heuristica de reserva. */
    private boolean aiClassified;

    /** Vencimiento del SLA, fijado automaticamente segun la prioridad detectada. */
    private Instant dueDate;

    /** Agente de la empresa que lo tiene asignado. */
    private String assignedToUserId;
    private String assignedToName;

    private List<CaseMessage> messages = new ArrayList<>();

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    /** Momento del ultimo mensaje: ordena la bandeja por actividad reciente. */
    private Instant lastMessageAt = Instant.now();

    public void addMessage(CaseMessage message) {
        this.messages.add(message);
        this.lastMessageAt = message.getCreatedAt();
        this.updatedAt = Instant.now();
    }

    public boolean isOverdue() {
        return dueDate != null && !status.isTerminal() && Instant.now().isAfter(dueDate);
    }

    /** Texto que se envia al BERT: asunto mas primer mensaje, que es lo que fija el tono. */
    public String classificationText() {
        String primero = messages.isEmpty() ? "" : messages.get(0).getBody();
        return (subject == null ? "" : subject) + ". " + primero;
    }

    // ------------------------------------------------------------------ mensaje embebido

    public static class CaseMessage {

        public enum Author {
            CLIENTE, EMPRESA
        }

        private Author author;
        private String authorId;
        private String authorName;
        private String body;
        private Instant createdAt = Instant.now();

        public CaseMessage() {
        }

        public CaseMessage(Author author, String authorId, String authorName, String body) {
            this.author = author;
            this.authorId = authorId;
            this.authorName = authorName;
            this.body = body;
        }

        public Author getAuthor() {
            return author;
        }

        public void setAuthor(Author author) {
            this.author = author;
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

    // ------------------------------------------------------------------ accesores

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    public String getAiReason() {
        return aiReason;
    }

    public void setAiReason(String aiReason) {
        this.aiReason = aiReason;
    }

    public boolean isAiClassified() {
        return aiClassified;
    }

    public void setAiClassified(boolean aiClassified) {
        this.aiClassified = aiClassified;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public String getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(String assignedToUserId) {
        this.assignedToUserId = assignedToUserId;
    }

    public String getAssignedToName() {
        return assignedToName;
    }

    public void setAssignedToName(String assignedToName) {
        this.assignedToName = assignedToName;
    }

    public List<CaseMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<CaseMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : messages;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}
