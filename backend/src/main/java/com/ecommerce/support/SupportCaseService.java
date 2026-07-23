package com.ecommerce.support;

import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.common.SequenceService;
import com.ecommerce.company.CompanyRepository;
import com.ecommerce.config.AppProperties;
import com.ecommerce.nlp.CasePrioritizationClient;
import com.ecommerce.notification.Notification;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.support.dto.SupportDtos.CaseDetail;
import com.ecommerce.support.dto.SupportDtos.CaseRow;
import com.ecommerce.support.dto.SupportMapper;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Casos de atencion, lado del cliente.
 *
 * <p>Al abrir un caso se clasifica de inmediato con el modelo BERT: de esa clasificacion salen la
 * prioridad, el sentimiento y —lo mas util para la empresa— el vencimiento del SLA, mas corto cuanto
 * mas urgente parezca. Asi la bandeja de la empresa ya nace ordenada por lo que no puede esperar.
 */
@Service
public class SupportCaseService {

    private static final Logger log = LoggerFactory.getLogger(SupportCaseService.class);

    private final SupportCaseRepository cases;
    private final OrderRepository orders;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final CasePrioritizationClient nlp;
    private final NotificationService notifications;
    private final SequenceService sequences;
    private final AppProperties properties;

    public SupportCaseService(SupportCaseRepository cases, OrderRepository orders,
                              UserRepository users, CompanyRepository companies,
                              CasePrioritizationClient nlp, NotificationService notifications,
                              SequenceService sequences, AppProperties properties) {
        this.cases = cases;
        this.orders = orders;
        this.users = users;
        this.companies = companies;
        this.nlp = nlp;
        this.notifications = notifications;
        this.sequences = sequences;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ abrir

    public CaseDetail open(AppPrincipal principal, String subject, String message, String orderId) {
        var customer = users.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("La cuenta no existe."));

        // El caso se dirige a una empresa. Si viene de un pedido, a la de ese pedido; si no, hay que
        // indicar el pedido, porque un caso sin destinatario no lo puede atender nadie.
        Order order = null;
        if (orderId != null && !orderId.isBlank()) {
            order = orders.findByIdAndCustomerId(orderId, principal.userId())
                    .orElseThrow(() -> ApiException.badRequest("ORDER_NOT_FOUND",
                            "Ese pedido no es tuyo o no existe."));
        } else {
            throw ApiException.badRequest("ORDER_REQUIRED",
                    "Indica el pedido sobre el que quieres abrir el caso.");
        }

        var soporte = new SupportCase();
        soporte.setNumber(nextCaseNumber());
        soporte.setCompanyId(order.getCompanyId());
        soporte.setCompanyName(order.getCompanyName());
        soporte.setCustomerId(customer.getId());
        soporte.setCustomerEmail(customer.getEmail());
        soporte.setCustomerName(customer.displayName().trim());
        soporte.setOrderId(order.getId());
        soporte.setOrderNumber(order.getNumber());
        soporte.setSubject(subject.trim());
        soporte.addMessage(new SupportCase.CaseMessage(
                SupportCase.CaseMessage.Author.CLIENTE, customer.getId(),
                customer.displayName().trim(), message.trim()));

        classify(soporte);
        cases.save(soporte);

        // Aviso a la empresa: hay un caso nuevo que atender.
        notifyCompany(soporte, "Caso nuevo %s".formatted(soporte.getNumber()),
                "%s abrio un caso: %s".formatted(soporte.getCustomerName(), soporte.getSubject()));

        return SupportMapper.toDetail(soporte);
    }

    /**
     * Clasifica el caso con el BERT y fija su SLA.
     *
     * <p>Se hace en la apertura y no en cada listado: recalcularlo continuamente seria caro y, sobre
     * todo, cambiante. El vencimiento debe ser estable para que la empresa pueda confiar en el orden.
     */
    private void classify(SupportCase soporte) {
        var item = new CasePrioritizationClient.CaseItem(
                soporte.getNumber(), soporte.classificationText(), soporte.getCreatedAt(), null);

        var respuesta = nlp.prioritize(List.of(item));
        var clasificacion = respuesta.items().isEmpty() ? null : respuesta.items().get(0);

        String prioridad = clasificacion == null ? "MEDIUM" : clasificacion.priority();
        soporte.setPriority(prioridad);
        soporte.setSentiment(clasificacion == null ? "NEUTRAL" : clasificacion.sentiment());
        soporte.setAiReason(clasificacion == null ? null : clasificacion.reason());
        // Solo se considera clasificado por IA si el modelo respondio de verdad.
        soporte.setAiClassified(!respuesta.degraded());

        int horas = properties.support().slaHoursFor(prioridad);
        soporte.setDueDate(soporte.getCreatedAt().plus(horas, ChronoUnit.HOURS));

        log.info("Caso {} clasificado como {} (SLA {}h, {})", soporte.getNumber(), prioridad, horas,
                respuesta.degraded() ? "heuristica" : "BERT");
    }

    // ------------------------------------------------------------------ conversar

    public CaseDetail addMessage(AppPrincipal principal, String caseId, String message) {
        var soporte = requireOwn(principal, caseId);

        if (soporte.getStatus() == CaseStatus.CERRADO) {
            throw ApiException.badRequest("CASE_CLOSED",
                    "Este caso esta cerrado. Abre uno nuevo si necesitas mas ayuda.");
        }

        soporte.addMessage(new SupportCase.CaseMessage(
                SupportCase.CaseMessage.Author.CLIENTE, principal.userId(),
                soporte.getCustomerName(), message.trim()));

        // Si el cliente responde a un caso que se daba por resuelto, vuelve a la cola.
        if (soporte.getStatus() == CaseStatus.RESUELTO
                || soporte.getStatus() == CaseStatus.ESPERANDO_CLIENTE) {
            soporte.setStatus(CaseStatus.EN_ATENCION);
        }
        cases.save(soporte);

        notifyCompany(soporte, "Nueva respuesta en el caso %s".formatted(soporte.getNumber()),
                "%s respondio en su caso.".formatted(soporte.getCustomerName()));

        return SupportMapper.toDetail(soporte);
    }

    // ------------------------------------------------------------------ consulta del cliente

    public PageResponse<CaseRow> myCases(AppPrincipal principal, int page, int size) {
        var result = cases.findByCustomerIdOrderByLastMessageAtDesc(
                principal.userId(), PageRequest.of(page, size));
        return PageResponse.of(result, SupportMapper::toRow);
    }

    public CaseDetail myCase(AppPrincipal principal, String caseId) {
        return SupportMapper.toDetail(requireOwn(principal, caseId));
    }

    // ------------------------------------------------------------------ apoyo

    private SupportCase requireOwn(AppPrincipal principal, String caseId) {
        return cases.findByIdAndCustomerId(caseId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("El caso no existe."));
    }

    /** Avisa al usuario root de la empresa. En la Fase 5 se afinara por departamento. */
    private void notifyCompany(SupportCase soporte, String titulo, String cuerpo) {
        users.findByCompanyIdAndRootIsTrue(soporte.getCompanyId())
                .map(User::getId)
                .ifPresent(userId -> notifications.push(userId, Notification.Type.CASE,
                        titulo, cuerpo, "/empresa/casos/" + soporte.getId()));
    }

    private String nextCaseNumber() {
        int year = Instant.now().atZone(java.time.ZoneOffset.UTC).getYear();
        return "CASO-%d-%06d".formatted(year, sequences.next("cases-" + year));
    }
}
