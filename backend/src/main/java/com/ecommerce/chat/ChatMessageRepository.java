package com.ecommerce.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    Page<ChatMessage> findByCompanyIdAndChannelOrderByCreatedAtDesc(
            String companyId, String channel, Pageable pageable);
}
