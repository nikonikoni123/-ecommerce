package com.ecommerce.company;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CompanyRepository extends MongoRepository<Company, String> {

    Optional<Company> findByNit(String nit);

    boolean existsByNit(String nit);

    boolean existsByEmailIgnoreCase(String email);
}
