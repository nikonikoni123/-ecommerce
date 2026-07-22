package com.ecommerce.company;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoleRepository extends MongoRepository<Role, String> {

    List<Role> findByCompanyId(String companyId);

    List<Role> findByIdIn(Collection<String> ids);

    Optional<Role> findByCompanyIdAndNameIgnoreCase(String companyId, String name);
}
