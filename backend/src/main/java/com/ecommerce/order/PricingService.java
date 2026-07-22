package com.ecommerce.order;

import com.ecommerce.config.AppProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Calculo de totales.
 *
 * <p>El orden de las operaciones es lo que decide cuanto paga el cliente, asi que se fija aqui de
 * forma explicita y en un solo sitio:
 *
 * <pre>
 *   subtotal       = suma de (precio unitario x cantidad)
 *   descuento      = subtotal x porcentaje acumulado
 *   base imponible = subtotal - descuento
 *   IVA            = base imponible x tipo          (sobre la base ya descontada)
 *   envio          = 0 si base imponible >= umbral, si no la tarifa plana
 *   total          = base imponible + IVA + envio
 * </pre>
 *
 * <p>Todo con {@link BigDecimal} y {@link RoundingMode#HALF_UP} a dos decimales. Con {@code double}
 * los centimos se pierden y las facturas dejan de cuadrar.
 */
@Service
public class PricingService {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int ESCALA = 2;

    private final DiscountService discountService;
    private final AppProperties properties;

    public PricingService(DiscountService discountService, AppProperties properties) {
        this.discountService = discountService;
        this.properties = properties;
    }

    /**
     * @param lines       lineas de un unico vendedor: el envio se cobra una vez por empresa
     * @param customerId  cliente, para el descuento de frecuente
     * @param randomOrder el pedido procede de la caja sorpresa
     */
    public Quote quote(List<Line> lines, String customerId, boolean randomOrder, Instant when) {
        BigDecimal subtotal = lines.stream()
                .map(Line::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        var discount = discountService.calculate(subtotal, customerId, randomOrder, when);

        BigDecimal taxableBase = subtotal.subtract(discount.amount())
                .max(BigDecimal.ZERO)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        BigDecimal taxRate = properties.pricing().taxRatePercent();
        BigDecimal taxAmount = taxableBase.multiply(taxRate)
                .divide(CIEN, ESCALA, RoundingMode.HALF_UP);

        BigDecimal shipping = shippingFor(taxableBase);

        BigDecimal total = taxableBase.add(taxAmount).add(shipping)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        return new Quote(subtotal, discount, taxableBase, taxRate, taxAmount, shipping, total);
    }

    /** El envio es gratis a partir del umbral, y se evalua sobre la base ya descontada. */
    public BigDecimal shippingFor(BigDecimal taxableBase) {
        if (taxableBase.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal umbral = properties.pricing().freeShippingThreshold();
        if (umbral != null && taxableBase.compareTo(umbral) >= 0) {
            return BigDecimal.ZERO;
        }
        return properties.pricing().shippingFlatRate().setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /** Importe de una linea, redondeado una sola vez. */
    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /** Entrada minima del calculo: solo hace falta el importe de la linea. */
    public record Line(String productId, BigDecimal unitPrice, int quantity) {

        public BigDecimal lineTotal() {
            return PricingService.lineTotal(unitPrice, quantity);
        }
    }

    public record Quote(
            BigDecimal subtotal,
            DiscountService.DiscountResult discount,
            BigDecimal taxableBase,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal shippingCost,
            BigDecimal total) {

        public boolean freeShipping() {
            return shippingCost.compareTo(BigDecimal.ZERO) == 0;
        }
    }
}
