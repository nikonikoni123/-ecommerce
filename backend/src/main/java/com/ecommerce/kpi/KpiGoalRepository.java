package com.ecommerce.kpi;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KpiGoalRepository extends MongoRepository<KpiGoal, String> {

    List<KpiGoal> findByCompanyIdOrderByCreatedAtDesc(String companyId);

    List<KpiGoal> findByCompanyIdAndTargetTypeAndTargetId(
            String companyId, KpiGoal.TargetType targetType, String targetId);

    Optional<KpiGoal> findByIdAndCompanyId(String id, String companyId);

    long countByCompanyId(String companyId);
}
