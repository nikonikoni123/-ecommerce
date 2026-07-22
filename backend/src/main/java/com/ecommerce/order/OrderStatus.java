package com.ecommerce.order;

/**
 * Estados del pedido, en el orden y con los nombres que fija la especificacion.
 *
 * <p>La Etapa 2 solo llega a {@link #PREPARANDO_ORDEN}: el resto de transiciones las gestiona la
 * empresa en la Etapa 3. Se declaran todos desde ahora para no tener que migrar documentos despues.
 */
public enum OrderStatus {

    PREPARANDO_ORDEN("Preparando orden", false),
    ALISTANDO_PEDIDO("Alistando pedido", false),
    ENVIANDO("Enviando", false),
    ENTREGADO("Entregado", true),
    CANCELADO("Servicio cancelado", true),
    REEMBOLSADO("Reembolsado", true);

    private final String label;

    /** Un estado final ya no admite mas transiciones y saca al pedido de "en proceso". */
    private final boolean terminal;

    OrderStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
