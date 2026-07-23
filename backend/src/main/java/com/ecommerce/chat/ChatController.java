package com.ecommerce.chat;

import com.ecommerce.chat.ChatService.ChatMessageView;
import com.ecommerce.common.PageResponse;
import com.ecommerce.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat general de la empresa.
 *
 * <p>Leer solo exige ser de la empresa; publicar exige {@code CHAT_GENERAL_POST}; comunicar a toda
 * la empresa exige {@code BROADCAST_SEND}. Asi root decide quien puede hablar y quien solo escucha.
 */
@RestController
@RequestMapping("/api/company/chat")
@Tag(name = "Chat de empresa", description = "Chat general y comunicados de root")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Historial del chat general y estado de permiso para publicar")
    public ChatFeed feed(@AuthenticationPrincipal AppPrincipal principal,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "30") int size) {
        var mensajes = service.history(principal, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return new ChatFeed(mensajes, service.canPost(principal),
                principal.has(com.ecommerce.security.Permission.BROADCAST_SEND));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CHAT_GENERAL_POST')")
    @Operation(summary = "Publicar un mensaje en el chat general")
    public ChatMessageView post(@AuthenticationPrincipal AppPrincipal principal,
                                @Valid @RequestBody MessageRequest request) {
        return service.post(principal, request.body());
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasAuthority('BROADCAST_SEND')")
    @Operation(summary = "Enviar un comunicado a toda la empresa")
    public ChatMessageView broadcast(@AuthenticationPrincipal AppPrincipal principal,
                                     @Valid @RequestBody MessageRequest request) {
        return service.broadcast(principal, request.body());
    }

    public record MessageRequest(
            @NotBlank(message = "Escribe el mensaje")
            @Size(min = 1, max = 2000, message = "El mensaje no puede superar los 2000 caracteres")
            String body) {
    }

    /**
     * @param canPost      el usuario puede publicar en el chat general
     * @param canBroadcast el usuario puede enviar comunicados a toda la empresa
     */
    public record ChatFeed(PageResponse<ChatMessageView> messages, boolean canPost,
                           boolean canBroadcast) {
    }
}
