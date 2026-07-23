package com.ecommerce.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.ApiException;
import com.ecommerce.common.SequenceService;
import com.ecommerce.company.CompanyRepository;
import com.ecommerce.config.AppProperties;
import com.ecommerce.nlp.CasePrioritizationClient;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * La apertura de un caso es donde se decide su prioridad y su vencimiento, asi que se comprueba que
 * la clasificacion del BERT se traduce en el SLA correcto y que el fallback deja el caso usable.
 */
@ExtendWith(MockitoExtension.class)
class SupportCaseServiceTest {

    private static final String CLIENTE = "cliente-1";

    @Mock private SupportCaseRepository cases;
    @Mock private OrderRepository orders;
    @Mock private UserRepository users;
    @Mock private CompanyRepository companies;
    @Mock private CasePrioritizationClient nlp;
    @Mock private NotificationService notifications;
    @Mock private SequenceService sequences;

    private SupportCaseService service;
    private AppPrincipal principal;

    @BeforeEach
    void setUp() {
        var support = new AppProperties.Support(24, 48, 72);
        var properties = new AppProperties("http://localhost:8080", "http://localhost:4200",
                "no-reply@test", false, null, null, null, null, support);

        service = new SupportCaseService(cases, orders, users, companies, nlp, notifications,
                sequences, properties);

        principal = new AppPrincipal(CLIENTE, "cliente@test.local", UserType.CUSTOMER, null, false,
                Set.of());

        var cliente = new User();
        cliente.setId(CLIENTE);
        cliente.setType(UserType.CUSTOMER);
        cliente.setEmail("cliente@test.local");
        cliente.setFirstName("Laura");
        cliente.setLastName("Gomez");
        lenient().when(users.findById(CLIENTE)).thenReturn(Optional.of(cliente));
        lenient().when(users.findByCompanyIdAndRootIsTrue(anyString()))
                .thenReturn(Optional.of(rootUser()));

        lenient().when(sequences.next(anyString())).thenReturn(1L);
        lenient().when(orders.findByIdAndCustomerId("order-1", CLIENTE))
                .thenReturn(Optional.of(order()));
        lenient().when(cases.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void unCasoDeAltaPrioridadRecibeElSlaMasCorto() {
        clasifica("HIGH", "NEGATIVE", false);

        var detalle = service.open(principal, "Producto roto",
                "Llego destrozado y quiero una solucion ya", "order-1");

        assertThat(detalle.priority()).isEqualTo("HIGH");
        assertThat(detalle.aiClassified()).isTrue();
        // 24h de SLA para HIGH: el vencimiento cae aproximadamente un dia despues.
        assertThat(Duration.between(Instant.now(), detalle.dueDate()).toHours())
                .isBetween(23L, 24L);
    }

    @Test
    void unaConsultaTrivialRecibeElSlaMasLargo() {
        clasifica("LOW", "POSITIVE", false);

        var detalle = service.open(principal, "Consulta", "Solo queria dar las gracias", "order-1");

        assertThat(detalle.priority()).isEqualTo("LOW");
        assertThat(Duration.between(Instant.now(), detalle.dueDate()).toHours())
                .isBetween(71L, 72L);
    }

    @Test
    void siElModeloNoRespondeElCasoSigueSiendoUsable() {
        // degraded=true: la respuesta vino de la heuristica, no del BERT.
        when(nlp.prioritize(anyList())).thenReturn(new CasePrioritizationClient.PrioritizeResponse(
                List.of(new CasePrioritizationClient.PrioritizedCase("c", "MEDIUM", 0.5, "NEUTRAL",
                        "estimacion")),
                "heuristica-local", true));

        var detalle = service.open(principal, "Pregunta", "Una pregunta cualquiera", "order-1");

        assertThat(detalle.priority()).isEqualTo("MEDIUM");
        // No se atribuye a la IA cuando en realidad fue la heuristica.
        assertThat(detalle.aiClassified()).isFalse();
        assertThat(detalle.dueDate()).isNotNull();
    }

    @Test
    void abrirUnCasoAvisaALaEmpresa() {
        clasifica("MEDIUM", "NEUTRAL", false);

        service.open(principal, "Asunto", "Mensaje de prueba", "order-1");

        verify(notifications).push(eq("root-1"), any(), anyString(), anyString(), anyString());
    }

    @Test
    void noSePuedeAbrirUnCasoSinPedido() {
        assertThatThrownBy(() -> service.open(principal, "Asunto", "Mensaje", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pedido");
    }

    @Test
    void noSePuedeAbrirCasoSobreUnPedidoAjeno() {
        when(orders.findByIdAndCustomerId("ajeno", CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(principal, "Asunto", "Mensaje", "ajeno"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no es tuyo");
    }

    @Test
    void responderAUnCasoResueltoLoReabre() {
        var soporte = casoEnEstado(CaseStatus.RESUELTO);
        when(cases.findByIdAndCustomerId("case-1", CLIENTE)).thenReturn(Optional.of(soporte));

        service.addMessage(principal, "case-1", "Sigo sin estar conforme");

        assertThat(soporte.getStatus()).isEqualTo(CaseStatus.EN_ATENCION);
    }

    @Test
    void noSePuedeEscribirEnUnCasoCerrado() {
        var soporte = casoEnEstado(CaseStatus.CERRADO);
        when(cases.findByIdAndCustomerId("case-1", CLIENTE)).thenReturn(Optional.of(soporte));

        assertThatThrownBy(() -> service.addMessage(principal, "case-1", "Hola"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cerrado");
    }

    @Test
    void elTextoDeClasificacionUneAsuntoYPrimerMensaje() {
        var captor = ArgumentCaptor.forClass(List.class);
        clasifica("MEDIUM", "NEUTRAL", false);

        service.open(principal, "Pedido tardio", "No ha llegado en la fecha prometida", "order-1");

        verify(nlp).prioritize(captor.capture());
        @SuppressWarnings("unchecked")
        var items = (List<CasePrioritizationClient.CaseItem>) captor.getValue();
        assertThat(items).singleElement()
                .satisfies(i -> assertThat(i.text())
                        .contains("Pedido tardio")
                        .contains("No ha llegado"));
    }

    // ------------------------------------------------------------------ apoyo

    private void clasifica(String prioridad, String sentimiento, boolean degradado) {
        when(nlp.prioritize(anyList())).thenReturn(new CasePrioritizationClient.PrioritizeResponse(
                List.of(new CasePrioritizationClient.PrioritizedCase(
                        "c", prioridad, 0.8, sentimiento, "motivo")),
                degradado ? "heuristica" : "bert", degradado));
    }

    private SupportCase casoEnEstado(CaseStatus estado) {
        var c = new SupportCase();
        c.setId("case-1");
        c.setNumber("CASO-2026-000001");
        c.setCustomerId(CLIENTE);
        c.setCustomerName("Laura Gomez");
        c.setCompanyId("company-1");
        c.setCompanyName("Empresa");
        c.setStatus(estado);
        return c;
    }

    private Order order() {
        var o = new Order();
        o.setId("order-1");
        o.setNumber("ORD-2026-000001");
        o.setCompanyId("company-1");
        o.setCompanyName("Laboratorio Botanico");
        return o;
    }

    private User rootUser() {
        var u = new User();
        u.setId("root-1");
        u.setType(UserType.COMPANY_MEMBER);
        u.setRoot(true);
        u.setCompanyId("company-1");
        return u;
    }
}
