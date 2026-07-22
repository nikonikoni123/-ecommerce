package com.ecommerce.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Product> findByCompanyId(String companyId, Pageable pageable);

    Optional<Product> findByIdAndCompanyId(String id, String companyId);

    List<Product> findByActiveIsTrue();

    long countByCompanyId(String companyId);
}
