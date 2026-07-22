package com.ecommerce.notification;

import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public Notification push(String recipientId, Notification.Type type, String title, String body,
                             String link) {
        return repository.save(new Notification(recipientId, type, title, body, link));
    }

    public PageResponse<NotificationView> list(String recipientId, int page, int size) {
        var result = repository.findByRecipientIdOrderByCreatedAtDesc(
                recipientId, PageRequest.of(page, size));
        return PageResponse.of(result, NotificationView::from);
    }

    public long unreadCount(String recipientId) {
        return repository.countByRecipientIdAndReadIsFalse(recipientId);
    }

    public void markRead(String id, String recipientId) {
        var notification = repository.findByIdAndRecipientId(id, recipientId)
                .orElseThrow(() -> ApiException.notFound("La notificacion no existe."));
        notification.setRead(true);
        repository.save(notification);
    }

    public record NotificationView(
            String id, String type, String title, String body, String link, boolean read,
            Instant createdAt) {

        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
                    n.getLink(), n.isRead(), n.getCreatedAt());
        }
    }
}
