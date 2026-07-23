package com.ecommerce.support;

import java.util.Set;

/**
 * Estados de un caso de atencion.
 *
 * <p>Como en los pedidos, las transiciones se declaran junto al dato: un caso cerrado no vuelve a
 * "abierto" por accidente, y la empresa no puede marcar como resuelto algo que ni ha tomado.
 */
public enum CaseStatus {

    /** Recien abierto por el cliente, todavia sin tomar. */
    ABIERTO("Abierto", false),
    /** Un agente lo tomo y lo esta atendiendo. */
    EN_ATENCION("En atencion", false),
    /** La empresa respondio y espera la reaccion del cliente. */
    ESPERANDO_CLIENTE("Esperando al cliente", false),
    /** La empresa lo da por resuelto. El cliente puede reabrirlo si no queda conforme. */
    RESUELTO("Resuelto", true),
    /** Cerrado definitivamente. */
    CERRADO("Cerrado", true);

    private final String label;
    private final boolean terminal;

    CaseStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    /** Transiciones que puede hacer la empresa desde este estado. */
    public Set<CaseStatus> companyTransitions() {
        return switch (this) {
            case ABIERTO -> Set.of(EN_ATENCION, RESUELTO, CERRADO);
            case EN_ATENCION -> Set.of(ESPERANDO_CLIENTE, RESUELTO, CERRADO);
            case ESPERANDO_CLIENTE -> Set.of(EN_ATENCION, RESUELTO, CERRADO);
            case RESUELTO -> Set.of(EN_ATENCION, CERRADO);
            case CERRADO -> Set.of();
        };
    }

    public boolean canTransitionTo(CaseStatus target) {
        return companyTransitions().contains(target);
    }

    /** Un caso deja de contar como "pendiente" para el SLA cuando llega a un estado final. */
    public boolean isTerminal() {
        return terminal;
    }

    public String getLabel() {
        return label;
    }
}
