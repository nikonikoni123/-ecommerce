package com.ecommerce.support;

import com.ecommerce.activity.ActivityService;
import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.notification.Notification;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.support.dto.SupportDtos.CaseDetail;
import com.ecommerce.support.dto.SupportDtos.CaseRow;
import com.ecommerce.support.dto.SupportDtos.CaseStats;
import com.ecommerce.support.dto.SupportMapper;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Pestana de atencion a casos de la empresa.
 *
 * <p>La especificacion pide que un modelo BERT organice los casos "por fecha, fecha de vencimiento y
 * prioridad". La prioridad y el sentimiento ya se calcularon al abrir el caso; aqui esa clasificacion
 * se traduce en un orden util: primero lo vencido, luego lo de mayor prioridad, y a igualdad lo mas
 * antiguo. La empresa tambien puede ordenar por cualquiera de los tres criterios sueltos.
 */
@Service
public class CompanySupportService {

    private static final Map<String, Integer> PESO_PRIORIDAD = Map.of("HIGH", 0, "MEDIUM", 1, "LOW", 2);

    private final SupportCaseRepository cases;
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final NotificationService notifications;
    private final ActivityService activityService;

    public CompanySupportService(SupportCaseRepository cases, MongoTemplate mongo,
                                 UserRepository users, NotificationService notifications,
                                 ActivityService activityService) {
        this.cases = cases;
        this.mongo = mongo;
        this.users = users;
        this.notifications = notifications;
        this.activityService = activityService;
    }

    // ------------------------------------------------------------------ bandeja

    public PageResponse<CaseRow> inbox(AppPrincipal principal, String status, String sort,
                                       int page, int size) {
        String companyId = companyOf(principal);

        var criteria = new ArrayList<Criteria>();
        criteria.add(Criteria.where("companyId").is(companyId));
        if (status != null && !status.isBlank()) {
            criteria.add(Criteria.where("status").is(parseStatus(status)));
        }
        var query = new Query(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));
        long total = mongo.count(query, SupportCase.class);

        if (esPrioridad(sort)) {
            // El orden por prioridad combina vencimiento y prioridad del BERT, y no es un campo
            // simple; se resuelve en memoria sobre un conjunto acotado.
            query.with(Sort.by(Sort.Direction.ASC, "dueDate")).limit(500);
            var candidatos = new ArrayList<>(mongo.find(query, SupportCase.class));
            candidatos.sort(Comparator
                    .comparingInt(this::pesoUrgencia)
                    .thenComparing(c -> c.getDueDate() == null ? Instant.MAX : c.getDueDate()));

            int desde = Math.min(page * size, candidatos.size());
            int hasta = Math.min(desde + size, candidatos.size());
            return PageResponse.of(candidatos.subList(desde, hasta).stream()
                    .map(SupportMapper::toRow).toList(), page, size, total);
        }

        query.with(sortFor(sort)).skip((long) page * size).limit(size);
        return PageResponse.of(mongo.find(query, SupportCase.class).stream()
                .map(SupportMapper::toRow).toList(), page, size, total);
    }

    public CaseStats stats(AppPrincipal principal) {
        String companyId = companyOf(principal);
        var abiertos = List.of(CaseStatus.ABIERTO);
        var enAtencion = List.of(CaseStatus.EN_ATENCION);
        var esperando = List.of(CaseStatus.ESPERANDO_CLIENTE);
        var resueltos = List.of(CaseStatus.RESUELTO, CaseStatus.CERRADO);
        var pendientes = List.of(CaseStatus.ABIERTO, CaseStatus.EN_ATENCION,
                CaseStatus.ESPERANDO_CLIENTE);

        return new CaseStats(
                cases.countByCompanyIdAndStatusIn(companyId, abiertos),
                cases.countByCompanyIdAndStatusIn(companyId, enAtencion),
                cases.countByCompanyIdAndDueDateBeforeAndStatusIn(companyId, Instant.now(), pendientes),
                cases.countByCompanyIdAndStatusIn(companyId, esperando),
                cases.countByCompanyIdAndStatusIn(companyId, resueltos));
    }

    public CaseDetail detail(AppPrincipal principal, String caseId) {
        return SupportMapper.toDetail(require(principal, caseId));
    }

    // ------------------------------------------------------------------ acciones

    public CaseDetail assign(AppPrincipal actor, String caseId, String agentUserId) {
        var soporte = require(actor, caseId);

        // Sin agente se asigna a quien actua; con agente, se comprueba que sea de la empresa.
        User agente = agentUserId == null || agentUserId.isBlank()
                ? users.findById(actor.userId()).orElse(null)
                : users.findById(agentUserId)
                        .filter(u -> soporte.getCompanyId().equals(u.getCompanyId()))
                        .orElseThrow(() -> ApiException.badRequest("AGENT_NOT_FOUND",
                                "Ese agente no pertenece a tu empresa."));

        if (agente != null) {
            soporte.setAssignedToUserId(agente.getId());
            soporte.setAssignedToName(agente.displayName().trim());
        }
        // Tomar un caso abierto lo pone en atencion.
        if (soporte.getStatus() == CaseStatus.ABIERTO) {
            soporte.setStatus(CaseStatus.EN_ATENCION);
        }
        cases.save(soporte);

        activityService.record(actor, "CASE_ASSIGN", "SupportCase", soporte.getId(),
                Map.of("numero", soporte.getNumber()));
        return SupportMapper.toDetail(soporte);
    }

    public CaseDetail reply(AppPrincipal actor, String caseId, String message) {
        var soporte = require(actor, caseId);

        if (soporte.getStatus() == CaseStatus.CERRADO) {
            throw ApiException.badRequest("CASE_CLOSED", "Este caso esta cerrado.");
        }

        var nombre = users.findById(actor.userId()).map(u -> u.displayName().trim())
                .orElse("Soporte");
        soporte.addMessage(new SupportCase.CaseMessage(
                SupportCase.CaseMessage.Author.EMPRESA, actor.userId(), nombre, message.trim()));

        // Responder deja el caso a la espera de la reaccion del cliente.
        if (soporte.getStatus() == CaseStatus.ABIERTO || soporte.getStatus() == CaseStatus.EN_ATENCION) {
            soporte.setStatus(CaseStatus.ESPERANDO_CLIENTE);
        }
        if (soporte.getAssignedToUserId() == null) {
            soporte.setAssignedToUserId(actor.userId());
            soporte.setAssignedToName(nombre);
        }
        cases.save(soporte);

        activityService.record(actor, "CASE_REPLY", "SupportCase", soporte.getId(),
                Map.of("numero", soporte.getNumber()));

        // La especificacion pide avisar al cliente de cualquier respuesta.
        notifications.push(soporte.getCustomerId(), Notification.Type.CASE,
                "Respuesta en tu caso %s".formatted(soporte.getNumber()),
                "%s respondio a tu caso.".formatted(soporte.getCompanyName()),
                "/casos/" + soporte.getId());

        return SupportMapper.toDetail(soporte);
    }

    public CaseDetail changeStatus(AppPrincipal actor, String caseId, String targetStatus) {
        var soporte = require(actor, caseId);
        var destino = parseStatus(targetStatus);

        if (soporte.getStatus() == destino) {
            throw ApiException.badRequest("SAME_STATUS", "El caso ya esta en ese estado.");
        }
        if (!soporte.getStatus().canTransitionTo(destino)) {
            throw ApiException.badRequest("INVALID_TRANSITION",
                    "No se puede pasar de \"%s\" a \"%s\".".formatted(
                            soporte.getStatus().getLabel(), destino.getLabel()));
        }

        soporte.setStatus(destino);
        soporte.setUpdatedAt(Instant.now());
        cases.save(soporte);

        activityService.record(actor, "CASE_STATUS_CHANGE", "SupportCase", soporte.getId(),
                Map.of("numero", soporte.getNumber(), "estado", destino.name()));

        notifications.push(soporte.getCustomerId(), Notification.Type.CASE,
                "Tu caso %s ahora esta \"%s\"".formatted(soporte.getNumber(), destino.getLabel()),
                "El estado de tu caso con %s cambio.".formatted(soporte.getCompanyName()),
                "/casos/" + soporte.getId());

        return SupportMapper.toDetail(soporte);
    }

    // ------------------------------------------------------------------ apoyo

    public SupportCase require(AppPrincipal principal, String caseId) {
        return cases.findByIdAndCompanyId(caseId, companyOf(principal))
                .orElseThrow(() -> ApiException.notFound("El caso no existe en tu empresa."));
    }

    private String companyOf(AppPrincipal principal) {
        if (principal.companyId() == null) {
            throw ApiException.forbidden("NOT_A_COMPANY_MEMBER",
                    "Esta seccion es exclusiva de las cuentas de empresa.");
        }
        return principal.companyId();
    }

    private boolean esPrioridad(String sort) {
        return sort == null || sort.isBlank() || "priority".equals(sort);
    }

    /** 0 vencido; si no, el peso de su prioridad (HIGH antes que LOW). */
    private int pesoUrgencia(SupportCase c) {
        if (c.isOverdue()) {
            return -1;
        }
        return PESO_PRIORIDAD.getOrDefault(c.getPriority(), 1);
    }

    private Sort sortFor(String sort) {
        return switch (sort) {
            case "date" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "dueDate" -> Sort.by(Sort.Direction.ASC, "dueDate");
            default -> Sort.by(Sort.Direction.DESC, "lastMessageAt");
        };
    }

    private CaseStatus parseStatus(String value) {
        try {
            return CaseStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("INVALID_STATUS", "Ese estado de caso no existe.");
        }
    }

    /** Estados disponibles, para que la interfaz sepa que transiciones ofrecer. */
    public List<CaseStatus> allStatuses() {
        return Arrays.asList(CaseStatus.values());
    }
}
