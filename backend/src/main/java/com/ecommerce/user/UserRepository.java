package com.ecommerce.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findByCompanyId(String companyId);

    Optional<User> findByCompanyIdAndRootIsTrue(String companyId);

    boolean existsByCompanyIdAndRootIsTrue(String companyId);
}
