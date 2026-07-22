package com.ecommerce.notification;

import com.ecommerce.auth.dto.AuthDtos.MessageResponse;
import com.ecommerce.common.PageResponse;
import com.ecommerce.notification.NotificationService.NotificationView;
import com.ecommerce.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notificaciones", description = "Pestana de notificaciones del usuario")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Listar las notificaciones, de la mas reciente a la mas antigua")
    public PageResponse<NotificationView> list(@AuthenticationPrincipal AppPrincipal principal,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(principal.userId(), page, Math.min(size, 100));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Numero de notificaciones sin leer, para el indicador del encabezado")
    public UnreadCount unreadCount(@AuthenticationPrincipal AppPrincipal principal) {
        return new UnreadCount(notificationService.unreadCount(principal.userId()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marcar una notificacion como leida")
    public MessageResponse markRead(@AuthenticationPrincipal AppPrincipal principal,
                                    @PathVariable String id) {
        notificationService.markRead(id, principal.userId());
        return new MessageResponse("Notificacion marcada como leida.");
    }

    public record UnreadCount(long count) {
    }
}
