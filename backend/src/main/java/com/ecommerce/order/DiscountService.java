package com.ecommerce.order;

import com.ecommerce.config.AppProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Motor de descuentos.
 *
 * <p>Reglas de la especificacion:
 * <ul>
 *   <li>Dentro de un rango de tiempo parametrizado, toda orden lleva un 10%.</li>
 *   <li>Si el pedido nace de la funcion de pedido aleatorio, un 50% mas.</li>
 *   <li>Si es cliente frecuente, un 5% adicional.</li>
 * </ul>
 *
 * <p>Dos decisiones que conviene tener presentes:
 * <ul>
 *   <li><b>La ventana condiciona los tres descuentos.</b> La especificacion dice que "los
 *       descuentos" —en plural— solo aplican si la orden queda registrada dentro del rango, asi que
 *       fuera de la ventana no se aplica ninguno, ni siquiera el de cliente frecuente.</li>
 *   <li><b>Se suman, no se encadenan</b>, con el techo configurado. El 5% se describe como
 *       "adicional", que es lo propio de una suma.</li>
 * </ul>
 *
 * <p>Devuelve el desglose completo y no solo el total, porque el carrito y la factura tienen que
 * poder justificar cada rebaja linea a linea.
 */
@Service
public class DiscountService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final PromotionWindowRepository promotionWindows;
    private final OrderRepository orders;
    private final AppProperties properties;

    public DiscountService(PromotionWindowRepository promotionWindows, OrderRepository orders,
                           AppProperties properties) {
        this.promotionWindows = promotionWindows;
        this.orders = orders;
        this.properties = properties;
    }

    /**
     * @param subtotal    suma de las lineas, antes de impuestos y envio
     * @param customerId  cliente que compra, para evaluar si es frecuente
     * @param randomOrder el pedido procede de la caja sorpresa
     * @param when        momento en que queda registrada la orden
     */
    public DiscountResult calculate(BigDecimal subtotal, String customerId, boolean randomOrder,
                                    Instant when) {
        var lines = new ArrayList<Line>();

        Optional<PromotionWindow> window = activeWindow(when);
        if (window.isEmpty()) {
            // Fuera de la ventana no hay ningun descuento, y se dice por que.
            return new DiscountResult(BigDecimal.ZERO, BigDecimal.ZERO, List.of(), false);
        }

        var promo = window.get();

        add(lines, "VENTANA", "Promocion " + promo.getName(), promo.getOrderDiscountPercent());

        if (randomOrder) {
            add(lines, "ALEATORIO", "Pedido sorpresa", promo.getRandomOrderDiscountPercent());
        }

        if (isFrequentCustomer(customerId)) {
            add(lines, "FRECUENTE", "Cliente frecuente",
                    properties.pricing().frequentCustomerPercent());
        }

        BigDecimal percent = lines.stream()
                .map(Line::percent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Techo global: por muchos descuentos que se acumulen, nunca se regala mas de lo previsto.
        BigDecimal max = properties.pricing().maxDiscountPercent();
        boolean capped = percent.compareTo(max) > 0;
        if (capped) {
            percent = max;
        }

        BigDecimal amount = percentOf(subtotal, percent);

        // El importe de cada linea se reparte sobre el subtotal para que el desglose sea legible.
        // Si actuo el techo, la suma de las partes no cuadra con el total, y por eso se avisa.
        var applied = lines.stream()
                .map(l -> new AppliedLine(l.code(), l.label(), l.percent(),
                        percentOf(subtotal, l.percent())))
                .toList();

        return new DiscountResult(percent, amount, applied, capped);
    }

    /** Si hay varias ventanas solapadas gana la mas generosa, que es lo que espera el cliente. */
    private Optional<PromotionWindow> activeWindow(Instant when) {
        return promotionWindows
                .findByActiveIsTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(when, when)
                .stream()
                .max(Comparator.comparing(PromotionWindow::getOrderDiscountPercent));
    }

    /** Cliente frecuente: acumula pedidos previos que no acabaron cancelados ni reembolsados. */
    public boolean isFrequentCustomer(String customerId) {
        if (customerId == null) {
            return false;
        }
        long previous = orders.countByCustomerIdAndStatusNotIn(
                customerId, List.of(OrderStatus.CANCELADO, OrderStatus.REEMBOLSADO));
        return previous >= properties.pricing().frequentCustomerOrders();
    }

    private void add(List<Line> lines, String code, String label, BigDecimal percent) {
        if (percent != null && percent.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new Line(code, label, percent));
        }
    }

    private BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent)
                .divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    private record Line(String code, String label, BigDecimal percent) {
    }

    /** Una rebaja concreta, con su porcentaje y su importe. */
    public record AppliedLine(String code, String label, BigDecimal percent, BigDecimal amount) {
    }

    /**
     * @param capped el techo recorto la suma, de modo que los importes por linea suman mas que
     *               {@code amount}
     */
    public record DiscountResult(
            BigDecimal percent,
            BigDecimal amount,
            List<AppliedLine> lines,
            boolean capped) {

        public boolean isEmpty() {
            return percent.compareTo(BigDecimal.ZERO) == 0;
        }
    }
}
