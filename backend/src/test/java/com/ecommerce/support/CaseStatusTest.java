package com.ecommerce.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Igual que en los pedidos, cada transicion del caso se prueba por separado. */
class CaseStatusTest {

    @Test
    void tomarUnCasoAbiertoLoPasaAAtencion() {
        assertThat(CaseStatus.ABIERTO.canTransitionTo(CaseStatus.EN_ATENCION)).isTrue();
    }

    @Test
    void unCasoResueltoPuedeReabrirse() {
        // Si el cliente no queda conforme, el caso vuelve a atencion.
        assertThat(CaseStatus.RESUELTO.canTransitionTo(CaseStatus.EN_ATENCION)).isTrue();
    }

    @Test
    void unCasoCerradoNoAdmiteNingunaTransicion() {
        assertThat(CaseStatus.CERRADO.companyTransitions()).isEmpty();
        for (var destino : CaseStatus.values()) {
            assertThat(CaseStatus.CERRADO.canTransitionTo(destino)).isFalse();
        }
    }

    @Test
    void noSePuedeReabrirLoQueYaSeCerro() {
        assertThat(CaseStatus.CERRADO.canTransitionTo(CaseStatus.EN_ATENCION)).isFalse();
        assertThat(CaseStatus.RESUELTO.canTransitionTo(CaseStatus.ABIERTO)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(CaseStatus.class)
    void ningunEstadoTransicionaASiMismo(CaseStatus estado) {
        assertThat(estado.canTransitionTo(estado)).isFalse();
    }

    @Test
    void resueltoYCerradoSonFinales() {
        assertThat(CaseStatus.RESUELTO.isTerminal()).isTrue();
        assertThat(CaseStatus.CERRADO.isTerminal()).isTrue();
        assertThat(CaseStatus.ABIERTO.isTerminal()).isFalse();
        assertThat(CaseStatus.EN_ATENCION.isTerminal()).isFalse();
        assertThat(CaseStatus.ESPERANDO_CLIENTE.isTerminal()).isFalse();
    }
}
