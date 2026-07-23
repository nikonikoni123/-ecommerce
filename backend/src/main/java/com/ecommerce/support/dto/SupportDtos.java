package com.ecommerce.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SupportDtos {

    private SupportDtos() {
    }

    // ---------------------------------------------------------------- cliente

    /**
     * Apertura de un caso. El pedido es opcional: se puede abrir un caso general.
     */
    public record OpenCaseRequest(
            @NotBlank(message = "El asunto es obligatorio")
            @Size(max = 150) String subject,

            @NotBlank(message = "Escribe tu mensaje")
            @Size(min = 5, max = 4000, message = "El mensaje debe tener entre 5 y 4000 caracteres")
            String message,

            /** Pedido al que se refiere el caso, si aplica. */
            String orderId) {
    }

    public record PostMessageRequest(
            @NotBlank(message = "Escribe tu mensaje")
            @Size(min = 1, max = 4000) String message) {
    }

    // ---------------------------------------------------------------- empresa

    public record AssignRequest(String agentUserId) {
    }

    public record ChangeCaseStatusRequest(
            @NotBlank(message = "Indica el estado nuevo") String status) {
    }

    // ---------------------------------------------------------------- vistas

    public record CaseMessageView(
            String author,       // CLIENTE o EMPRESA
            String authorName,
            String body,
            Instant createdAt) {
    }

    /** Fila de una lista de casos, sin los mensajes. */
    public record CaseRow(
            String id,
            String number,
            String subject,
            String status,
            String statusLabel,
            boolean terminal,
            String priority,
            String sentiment,
            boolean aiClassified,
            Instant dueDate,
            boolean overdue,
            String customerName,
            String companyName,
            String orderNumber,
            String assignedToName,
            int messageCount,
            Instant lastMessageAt,
            Instant createdAt) {
    }

    /** Caso completo con su hilo. */
    public record CaseDetail(
            String id,
            String number,
            String subject,
            String status,
            String statusLabel,
            boolean terminal,
            String priority,
            String sentiment,
            String aiReason,
            boolean aiClassified,
            Instant dueDate,
            boolean overdue,
            String customerName,
            String customerEmail,
            String companyName,
            String orderId,
            String orderNumber,
            String assignedToName,
            List<String> allowedTransitions,
            List<CaseMessageView> messages,
            Instant createdAt) {
    }

    /** Contadores de la bandeja de la empresa. */
    public record CaseStats(long open, long inProgress, long overdue, long waiting, long resolved) {
    }
}
