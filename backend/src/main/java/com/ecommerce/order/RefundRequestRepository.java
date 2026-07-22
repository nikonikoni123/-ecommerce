package com.ecommerce.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RefundRequestRepository extends MongoRepository<RefundRequest, String> {

    Page<RefundRequest> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);

    Page<RefundRequest> findByCompanyIdAndStatusOrderByCreatedAtDesc(
            String companyId, RefundRequest.Status status, Pageable pageable);

    List<RefundRequest> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    Optional<RefundRequest> findByIdAndCompanyId(String id, String companyId);

    /** Impide que un mismo pedido acumule varias solicitudes abiertas. */
    boolean existsByOrderIdAndStatus(String orderId, RefundRequest.Status status);

    long countByCompanyIdAndStatus(String companyId, RefundRequest.Status status);
}
