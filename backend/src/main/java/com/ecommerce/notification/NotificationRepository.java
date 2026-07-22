package com.ecommerce.notification;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(String id, String recipientId);

    long countByRecipientIdAndReadIsFalse(String recipientId);
}
