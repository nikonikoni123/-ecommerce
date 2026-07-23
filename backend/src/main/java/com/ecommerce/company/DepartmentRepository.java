package com.ecommerce.company;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DepartmentRepository extends MongoRepository<Department, String> {

    List<Department> findByCompanyId(String companyId);

    Optional<Department> findByIdAndCompanyId(String id, String companyId);

    Optional<Department> findByCompanyIdAndNameIgnoreCase(String companyId, String name);

    long countByCompanyId(String companyId);
}
