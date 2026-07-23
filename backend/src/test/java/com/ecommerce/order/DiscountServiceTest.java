package com.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.config.AppProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El motor de descuentos decide cuanto dinero deja de cobrarse, asi que cada regla se prueba por
 * separado y tambien su combinacion.
 */
@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    private static final BigDecimal SUBTOTAL = new BigDecimal("100000");
    private static final String CLIENTE = "cliente-1";

    @Mock
    private PromotionWindowRepository promotionWindows;

    @Mock
    private OrderRepository orders;

    private DiscountService service;

    @BeforeEach
    void setUp() {
        var pricing = new AppProperties.Pricing(
                new BigDecimal("19"), new BigDecimal("15000"), new BigDecimal("200000"),
                3, new BigDecimal("5"), new BigDecimal("65"));
        var properties = new AppProperties("http://localhost:8080", "http://localhost:4200",
                "no-reply@test", false, null, null, pricing,
                new AppProperties.Orders(5, 30, 24),
                new AppProperties.Support(24, 48, 72));

        service = new DiscountService(promotionWindows, orders, properties);

        // Por defecto el cliente no es frecuente; cada prueba lo ajusta si lo necesita.
        lenient().when(orders.countByCustomerIdAndStatusNotIn(anyString(), anyList())).thenReturn(0L);
    }

    @Test
    void sinVentanaActivaNoSeAplicaNingunDescuento() {
        when(promotionWindows.findByActiveIsTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                any(), any())).thenReturn(List.of());

        var result = service.calculate(SUBTOTAL, CLIENTE, false, Instant.now());

        assertThat(result.percent()).isEqualByComparingTo("0");
        assertThat(result.amount()).isEqualByComparingTo("0");
        assertThat(result.lines()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void fueraDeLaVentanaTampocoSeAplicaElDeClienteFrecuente() {
        // La especificacion condiciona "los descuentos", en plural, a estar dentro del rango.
        when(promotionWindows.findByActiveIsTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                any(), any())).thenReturn(List.of());

        var result = service.calculate(SUBTOTAL, CLIENTE, true, Instant.now());

        assertThat(result.percent()).isEqualByComparingTo("0");
        // Ni siquiera se consulta el historial: sin ventana la respuesta es cero sin mas trabajo.
        verify(orders, never()).countByCustomerIdAndStatusNotIn(anyString(), anyList());
    }

    @Test
    void dentroDeLaVentanaSeAplicaElDiezPorCiento() {
        conVentana();

        var result = service.calculate(SUBTOTAL, CLIENTE, false, Instant.now());

        assertThat(result.percent()).isEqualByComparingTo("10");
        assertThat(result.amount()).isEqualByComparingTo("10000.00");
        assertThat(result.lines()).singleElement()
                .satisfies(l -> assertThat(l.code()).isEqualTo("VENTANA"));
    }

    @Test
    void elPedidoSorpresaSumaCincuentaPuntos() {
        conVentana();

        var result = service.calculate(SUBTOTAL, CLIENTE, true, Instant.now());

        assertThat(result.percent()).isEqualByComparingTo("60");
        assertThat(result.amount()).isEqualByComparingTo("60000.00");
        assertThat(result.lines()).extracting("code").containsExactly("VENTANA", "ALEATORIO");
    }

    @Test
    void elClienteFrecuenteSumaCincoPuntosMas() {
        conVentana();
        when(orders.countByCustomerIdAndStatusNotIn(anyString(), anyList())).thenReturn(3L);

        var result = service.calculate(SUBTOTAL, CLIENTE, false, Instant.now());

        assertThat(result.percent()).isEqualByComparingTo("15");
        assertThat(result.lines()).extracting("code").containsExactly("VENTANA", "FRECUENTE");
    }

    @Test
    void losTresDescuentosSumanExactamenteElTecho() {
        conVentana();
        when(orders.countByCustomerIdAndStatusNotIn(anyString(), anyList())).thenReturn(3L);

        var result = service.calculate(SUBTOTAL, CLIENTE, true, Instant.now());

        // 10 + 50 + 5 = 65, que coincide con el techo configurado.
        assertThat(result.percent()).isEqualByComparingTo("65");
        assertThat(result.capped()).isFalse();

        // El desglose tiene que cuadrar con el importe aplicado.
        var suma = result.lines().stream()
                .map(DiscountService.AppliedLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo(result.amount());
    }

    @Test
    void elTechoRecortaCuandoLaVentanaEsMasGenerosa() {
        // Una promocion agresiva: 30 + 50 + 5 = 85, por encima del techo del 65.
        conVentana(new BigDecimal("30"), new BigDecimal("50"));
        when(orders.countByCustomerIdAndStatusNotIn(anyString(), anyList())).thenReturn(10L);

        var result = service.calculate(SUBTOTAL, CLIENTE, true, Instant.now());

        assertThat(result.percent()).isEqualByComparingTo("65");
        assertThat(result.amount()).isEqualByComparingTo("65000.00");
        assertThat(result.capped()).isTrue();
    }

    @Test
    void conVariasVentanasSolapadasGanaLaMasGenerosa() {
        when(promotionWindows.findByActiveIsTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                any(), any())).thenReturn(List.of(
                        window(new BigDecimal("10"), new BigDecimal("50"), "Normal"),
                        window(new BigDecimal("25"), new BigDecimal("50"), "Black Friday")));

        var result = service.calculate(SUBTOTAL, CLIENTE, false, Instant.now());

        assertThat(result.percent()).isEqualByComparingTo("25");
        assertThat(result.lines().get(0).label()).contains("Black Friday");
    }

    @Test
    void justoPorDebajoDelUmbralNoEsClienteFrecuente() {
        conVentana();
        when(orders.countByCustomerIdAndStatusNotIn(anyString(), anyList())).thenReturn(2L);

        var result = service.calculate(SUBTOTAL, CLIENTE, false, Instant.now());

        assertThat(result.percent()).isEqualByComparingTo("10");
        assertThat(result.lines()).extracting("code").doesNotContain("FRECUENTE");
    }

    // ------------------------------------------------------------------ apoyo

    private void conVentana() {
        conVentana(new BigDecimal("10"), new BigDecimal("50"));
    }

    private void conVentana(BigDecimal orden, BigDecimal aleatorio) {
        when(promotionWindows.findByActiveIsTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                any(), any())).thenReturn(List.of(window(orden, aleatorio, "Prueba")));
    }

    private PromotionWindow window(BigDecimal orden, BigDecimal aleatorio, String nombre) {
        var w = new PromotionWindow();
        w.setName(nombre);
        w.setStartsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        w.setEndsAt(Instant.now().plus(1, ChronoUnit.DAYS));
        w.setOrderDiscountPercent(orden);
        w.setRandomOrderDiscountPercent(aleatorio);
        w.setActive(true);
        return w;
    }
}
