package com.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * La maquina de estados es la que impide que un pedido entregado vuelva a "preparando" o que uno
 * cancelado siga avanzando, asi que cada arista se prueba de forma explicita.
 */
class OrderStatusTest {

    @Test
    void elAvanceNormalEsSecuencial() {
        assertThat(OrderStatus.PREPARANDO_ORDEN.canTransitionTo(OrderStatus.ALISTANDO_PEDIDO)).isTrue();
        assertThat(OrderStatus.ALISTANDO_PEDIDO.canTransitionTo(OrderStatus.ENVIANDO)).isTrue();
        assertThat(OrderStatus.ENVIANDO.canTransitionTo(OrderStatus.ENTREGADO)).isTrue();
    }

    @Test
    void noSePuedenSaltarPasos() {
        assertThat(OrderStatus.PREPARANDO_ORDEN.canTransitionTo(OrderStatus.ENVIANDO)).isFalse();
        assertThat(OrderStatus.PREPARANDO_ORDEN.canTransitionTo(OrderStatus.ENTREGADO)).isFalse();
        assertThat(OrderStatus.ALISTANDO_PEDIDO.canTransitionTo(OrderStatus.ENTREGADO)).isFalse();
    }

    @Test
    void noSePuedeRetroceder() {
        assertThat(OrderStatus.ENVIANDO.canTransitionTo(OrderStatus.ALISTANDO_PEDIDO)).isFalse();
        assertThat(OrderStatus.ENTREGADO.canTransitionTo(OrderStatus.ENVIANDO)).isFalse();
        assertThat(OrderStatus.ALISTANDO_PEDIDO.canTransitionTo(OrderStatus.PREPARANDO_ORDEN)).isFalse();
    }

    @Test
    void cancelarSoloAntesDeQueSalgaElPaquete() {
        assertThat(OrderStatus.PREPARANDO_ORDEN.canTransitionTo(OrderStatus.CANCELADO)).isTrue();
        assertThat(OrderStatus.ALISTANDO_PEDIDO.canTransitionTo(OrderStatus.CANCELADO)).isTrue();
        // Ya en camino o entregado, la via es el reembolso, no la cancelacion.
        assertThat(OrderStatus.ENVIANDO.canTransitionTo(OrderStatus.CANCELADO)).isFalse();
        assertThat(OrderStatus.ENTREGADO.canTransitionTo(OrderStatus.CANCELADO)).isFalse();
    }

    @Test
    void reembolsarSoloDespuesDeEntregar() {
        assertThat(OrderStatus.ENTREGADO.canTransitionTo(OrderStatus.REEMBOLSADO)).isTrue();
        assertThat(OrderStatus.PREPARANDO_ORDEN.canTransitionTo(OrderStatus.REEMBOLSADO)).isFalse();
        assertThat(OrderStatus.ENVIANDO.canTransitionTo(OrderStatus.REEMBOLSADO)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELADO", "REEMBOLSADO"})
    void losEstadosCerradosNoAdmitenMasTransiciones(OrderStatus cerrado) {
        assertThat(cerrado.allowedTransitions()).isEmpty();
        for (var destino : OrderStatus.values()) {
            assertThat(cerrado.canTransitionTo(destino)).isFalse();
        }
    }

    @Test
    void losProductosSoloSeTocanAntesDeQueSalgaElPaquete() {
        assertThat(OrderStatus.PREPARANDO_ORDEN.allowsItemChanges()).isTrue();
        assertThat(OrderStatus.ALISTANDO_PEDIDO.allowsItemChanges()).isTrue();
        assertThat(OrderStatus.ENVIANDO.allowsItemChanges()).isFalse();
        assertThat(OrderStatus.ENTREGADO.allowsItemChanges()).isFalse();
        assertThat(OrderStatus.CANCELADO.allowsItemChanges()).isFalse();
    }

    @Test
    void cancelarYReembolsarDevuelvenElStock() {
        assertThat(OrderStatus.CANCELADO.restoresStock()).isTrue();
        assertThat(OrderStatus.REEMBOLSADO.restoresStock()).isTrue();
        assertThat(OrderStatus.ENTREGADO.restoresStock()).isFalse();
        assertThat(OrderStatus.ENVIANDO.restoresStock()).isFalse();
    }

    @Test
    void laRutaDeEntregaTieneLosCuatroHitosEnOrden() {
        assertThat(OrderStatus.deliveryPath()).containsExactly(
                OrderStatus.PREPARANDO_ORDEN, OrderStatus.ALISTANDO_PEDIDO,
                OrderStatus.ENVIANDO, OrderStatus.ENTREGADO);
    }

    @Test
    void ningunEstadoPuedeTransicionarASiMismo() {
        for (var estado : OrderStatus.values()) {
            assertThat(estado.canTransitionTo(estado))
                    .as("%s no deberia poder pasar a si mismo", estado)
                    .isFalse();
        }
    }
}
