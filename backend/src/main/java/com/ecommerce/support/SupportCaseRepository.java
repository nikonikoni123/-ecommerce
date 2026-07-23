package com.ecommerce.support;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupportCaseRepository extends MongoRepository<SupportCase, String> {

    // --- cliente ---
    Page<SupportCase> findByCustomerIdOrderByLastMessageAtDesc(String customerId, Pageable pageable);

    Optional<SupportCase> findByIdAndCustomerId(String id, String customerId);

    // --- empresa ---
    Page<SupportCase> findByCompanyIdOrderByLastMessageAtDesc(String companyId, Pageable pageable);

    Page<SupportCase> findByCompanyIdAndStatusInOrderByLastMessageAtDesc(
            String companyId, List<CaseStatus> statuses, Pageable pageable);

    Optional<SupportCase> findByIdAndCompanyId(String id, String companyId);

    long countByCompanyIdAndStatusIn(String companyId, List<CaseStatus> statuses);

    long countByCompanyIdAndDueDateBeforeAndStatusIn(
            String companyId, java.time.Instant when, List<CaseStatus> statuses);
}
