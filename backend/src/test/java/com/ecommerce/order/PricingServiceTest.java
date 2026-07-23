package com.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ecommerce.config.AppProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El orden de las operaciones decide el importe final, asi que se fija con pruebas: el IVA va sobre
 * la base ya descontada, y el envio se decide con esa misma base.
 */
@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    private static final String CLIENTE = "cliente-1";

    @Mock
    private DiscountService discountService;

    private PricingService service;

    @BeforeEach
    void setUp() {
        var pricing = new AppProperties.Pricing(
                new BigDecimal("19"), new BigDecimal("15000"), new BigDecimal("200000"),
                3, new BigDecimal("5"), new BigDecimal("65"));
        var properties = new AppProperties("http://localhost:8080", "http://localhost:4200",
                "no-reply@test", false, null, null, pricing,
                new AppProperties.Orders(5, 30, 24),
                new AppProperties.Support(24, 48, 72));

        service = new PricingService(discountService, properties);
    }

    @Test
    void sinDescuentoElIvaSeCalculaSobreElSubtotal() {
        sinDescuento();

        var quote = service.quote(List.of(linea("100000", 1)), CLIENTE, false, Instant.now());

        assertThat(quote.subtotal()).isEqualByComparingTo("100000.00");
        assertThat(quote.taxableBase()).isEqualByComparingTo("100000.00");
        assertThat(quote.taxAmount()).isEqualByComparingTo("19000.00");
        assertThat(quote.shippingCost()).isEqualByComparingTo("15000");   // no llega al umbral
        assertThat(quote.total()).isEqualByComparingTo("134000.00");
    }

    @Test
    void elIvaSeCalculaSobreLaBaseYaDescontada() {
        // Con 10% sobre 100.000 la base es 90.000, y el IVA debe salir de ahi, no de 100.000.
        conDescuento("10", "10000.00");

        var quote = service.quote(List.of(linea("100000", 1)), CLIENTE, false, Instant.now());

        assertThat(quote.taxableBase()).isEqualByComparingTo("90000.00");
        assertThat(quote.taxAmount()).isEqualByComparingTo("17100.00");   // 90.000 x 19%
        assertThat(quote.total()).isEqualByComparingTo("122100.00");      // 90.000 + 17.100 + 15.000
    }

    @Test
    void elEnvioEsGratisAlAlcanzarElUmbral() {
        sinDescuento();

        var quote = service.quote(List.of(linea("200000", 1)), CLIENTE, false, Instant.now());

        assertThat(quote.shippingCost()).isEqualByComparingTo("0");
        assertThat(quote.freeShipping()).isTrue();
        assertThat(quote.total()).isEqualByComparingTo("238000.00");
    }

    @Test
    void elUmbralDeEnvioSeEvaluaSobreLaBaseDescontadaNoSobreElSubtotal() {
        // 210.000 supera el umbral, pero tras un 10% la base baja a 189.000 y el envio se cobra.
        conDescuento("10", "21000.00");

        var quote = service.quote(List.of(linea("210000", 1)), CLIENTE, false, Instant.now());

        assertThat(quote.taxableBase()).isEqualByComparingTo("189000.00");
        assertThat(quote.shippingCost()).isEqualByComparingTo("15000");
    }

    @Test
    void variasLineasSeSumanAntesDeAplicarNada() {
        sinDescuento();

        var quote = service.quote(
                List.of(linea("89000", 2), linea("142000", 1)), CLIENTE, false, Instant.now());

        assertThat(quote.subtotal()).isEqualByComparingTo("320000.00");
        assertThat(quote.shippingCost()).isEqualByComparingTo("0");
        assertThat(quote.total()).isEqualByComparingTo("380800.00");   // 320.000 x 1,19
    }

    @Test
    void losImportesSeRedondeanADosDecimales() {
        sinDescuento();

        // 33.333,33 x 3 = 99.999,99 y su IVA lleva mas decimales de los admitidos.
        var quote = service.quote(List.of(linea("33333.33", 3)), CLIENTE, false, Instant.now());

        assertThat(quote.subtotal()).isEqualByComparingTo("99999.99");
        assertThat(quote.taxAmount().scale()).isEqualTo(2);
        assertThat(quote.total().scale()).isEqualTo(2);
        assertThat(quote.taxAmount()).isEqualByComparingTo("19000.00");
    }

    @Test
    void unDescuentoQueSuperaElSubtotalNoDejaLaBaseNegativa() {
        conDescuento("100", "150000.00");

        var quote = service.quote(List.of(linea("100000", 1)), CLIENTE, false, Instant.now());

        assertThat(quote.taxableBase()).isEqualByComparingTo("0.00");
        assertThat(quote.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(quote.shippingCost()).isEqualByComparingTo("0");
        assertThat(quote.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void elImporteDeLineaSeCalculaConPrecisionExacta() {
        assertThat(PricingService.lineTotal(new BigDecimal("0.1"), 3))
                .isEqualByComparingTo("0.30");   // con double saldria 0.30000000000000004
    }

    // ------------------------------------------------------------------ apoyo

    private void sinDescuento() {
        when(discountService.calculate(any(), anyString(), anyBoolean(), any()))
                .thenReturn(new DiscountService.DiscountResult(
                        BigDecimal.ZERO, BigDecimal.ZERO, List.of(), false));
    }

    private void conDescuento(String percent, String amount) {
        when(discountService.calculate(any(), anyString(), anyBoolean(), any()))
                .thenReturn(new DiscountService.DiscountResult(
                        new BigDecimal(percent), new BigDecimal(amount),
                        List.of(new DiscountService.AppliedLine("VENTANA", "Promocion",
                                new BigDecimal(percent), new BigDecimal(amount))),
                        false));
    }

    private PricingService.Line linea(String precio, int cantidad) {
        return new PricingService.Line("p", new BigDecimal(precio), cantidad);
    }
}
