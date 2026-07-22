package com.ecommerce.order;

import java.util.List;
import java.util.Set;

/**
 * Estados del pedido, con los nombres que fija la especificacion.
 *
 * <p>Las transiciones permitidas se declaran aqui y no en el servicio: asi la regla vive junto al
 * dato que gobierna y no puede saltarsela quien llame por otra via. Sin esta validacion, un pedido
 * ya entregado podria volver a "preparando", o uno cancelado seguir avanzando.
 */
public enum OrderStatus {

    PREPARANDO_ORDEN("Preparando orden", false),
    ALISTANDO_PEDIDO("Alistando pedido", false),
    ENVIANDO("Enviando", false),
    ENTREGADO("Entregado", true),
    CANCELADO("Servicio cancelado", true),
    REEMBOLSADO("Reembolsado", true);

    private final String label;

    /** Un estado final saca al pedido de "en proceso"; solo el reembolso parte de otro final. */
    private final boolean terminal;

    OrderStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    /**
     * Estados a los que puede pasar la empresa desde este.
     *
     * <p>El avance es secuencial; cancelar solo tiene sentido mientras el paquete no haya salido, y
     * reembolsar solo despues de entregar, que es cuando el cliente puede saber que no era lo que
     * esperaba.
     */
    public Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case PREPARANDO_ORDEN -> Set.of(ALISTANDO_PEDIDO, CANCELADO);
            case ALISTANDO_PEDIDO -> Set.of(ENVIANDO, CANCELADO);
            case ENVIANDO -> Set.of(ENTREGADO);
            case ENTREGADO -> Set.of(REEMBOLSADO);
            case CANCELADO, REEMBOLSADO -> Set.of();
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    /** Los productos solo se pueden tocar mientras el pedido no haya salido ni se haya cerrado. */
    public boolean allowsItemChanges() {
        return this == PREPARANDO_ORDEN || this == ALISTANDO_PEDIDO;
    }

    /** Cancelar o reembolsar devuelve la mercancia al catalogo. */
    public boolean restoresStock() {
        return this == CANCELADO || this == REEMBOLSADO;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return terminal;
    }

    /** Ruta normal de entrega, para pintar la linea de tiempo del cliente. */
    public static List<OrderStatus> deliveryPath() {
        return List.of(PREPARANDO_ORDEN, ALISTANDO_PEDIDO, ENVIANDO, ENTREGADO);
    }
}
