package com.ecommerce.activity;

import com.ecommerce.common.PageResponse;
import com.ecommerce.security.AppPrincipal;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Registro y consulta de la actividad de los miembros de cada empresa. */
@Service
public class ActivityService {

    private final ActivityLogRepository repository;

    public ActivityService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    public void record(AppPrincipal actor, String action, String targetType, String targetId,
                       Map<String, String> metadata) {
        if (actor.companyId() == null) {
            return;   // los clientes no alimentan el registro de actividad de ninguna empresa
        }
        var entry = new ActivityLog(actor.companyId(), actor.userId(), actor.email(), action,
                targetType, targetId);
        entry.setMetadata(metadata);
        repository.save(entry);
    }

    public void record(AppPrincipal actor, String action, String targetType, String targetId) {
        record(actor, action, targetType, targetId, Map.of());
    }

    public PageResponse<ActivityView> list(String companyId, String userId, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = userId == null || userId.isBlank()
                ? repository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                : repository.findByCompanyIdAndUserIdOrderByCreatedAtDesc(companyId, userId, pageable);
        return PageResponse.of(result, ActivityView::from);
    }

    public record ActivityView(
            String id, String userId, String userEmail, String action, String targetType,
            String targetId, Map<String, String> metadata, Instant createdAt) {

        static ActivityView from(ActivityLog log) {
            return new ActivityView(log.getId(), log.getUserId(), log.getUserEmail(), log.getAction(),
                    log.getTargetType(), log.getTargetId(), log.getMetadata(), log.getCreatedAt());
        }
    }
}
