package com.ecommerce.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByIdAndCustomerId(String id, String customerId);

    Optional<Order> findByNumber(String number);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    /** Pedidos en proceso o ya cerrados, segun la lista de estados que se pase. */
    Page<Order> findByCustomerIdAndStatusInOrderByCreatedAtDesc(
            String customerId, List<OrderStatus> statuses, Pageable pageable);

    long countByCustomerId(String customerId);

    /** Base del descuento de cliente frecuente: pedidos previos que no se anularon. */
    long countByCustomerIdAndStatusNotIn(String customerId, List<OrderStatus> statuses);

    // --- Consultas de la Etapa 3, ya disponibles para el panel de la empresa ---

    Page<Order> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);

    Optional<Order> findByIdAndCompanyId(String id, String companyId);
}
