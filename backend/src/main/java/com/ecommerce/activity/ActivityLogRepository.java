package com.ecommerce.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {

    Page<ActivityLog> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);

    Page<ActivityLog> findByCompanyIdAndUserIdOrderByCreatedAtDesc(
            String companyId, String userId, Pageable pageable);
}
