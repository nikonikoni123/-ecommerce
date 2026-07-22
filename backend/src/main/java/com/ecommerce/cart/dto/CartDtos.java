package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    /** Tope por linea: evita que un error de tecleo bloquee el stock de un producto entero. */
    private static final int MAX_UNIDADES = 99;

    public record AddItemRequest(
            @NotBlank(message = "Indica el producto") String productId,
            @NotNull(message = "Indica la cantidad")
            @Min(value = 1, message = "La cantidad minima es 1")
            @Max(value = MAX_UNIDADES, message = "La cantidad maxima por producto es 99")
            Integer quantity) {
    }

    public record UpdateQuantityRequest(
            @NotNull(message = "Indica la cantidad")
            @Min(value = 1, message = "La cantidad minima es 1")
            @Max(value = MAX_UNIDADES, message = "La cantidad maxima por producto es 99")
            Integer quantity) {
    }

    /** Sustituye un producto del carrito por otro, conservando la posicion. */
    public record ReplaceItemRequest(
            @NotBlank(message = "Indica el producto nuevo") String newProductId,
            @NotNull(message = "Indica la cantidad")
            @Min(value = 1, message = "La cantidad minima es 1")
            @Max(value = MAX_UNIDADES, message = "La cantidad maxima por producto es 99")
            Integer quantity) {
    }

    /**
     * Linea del carrito con los datos vivos del producto.
     *
     * @param available   el producto sigue publicado y con stock suficiente
     * @param stockLeft   unidades disponibles, para avisar antes de llegar al pago
     */
    public record CartLine(
            String productId,
            String name,
            String slug,
            String imageUrl,
            String companyId,
            String companyName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            String currency,
            boolean available,
            int stockLeft) {
    }

    /** Totales de un vendedor. El envio se cobra una vez por empresa. */
    public record CompanyTotals(
            String companyId,
            String companyName,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxableBase,
            BigDecimal taxAmount,
            BigDecimal shippingCost,
            BigDecimal total) {
    }

    public record DiscountLine(String code, String label, BigDecimal percent, BigDecimal amount) {
    }

    /**
     * Carrito completo con su cotizacion.
     *
     * @param blocked   hay lineas sin stock o no disponibles: no se puede pagar hasta resolverlas
     * @param capped    el techo de descuento recorto la suma de porcentajes
     */
    public record CartView(
            List<CartLine> lines,
            List<CompanyTotals> byCompany,
            List<DiscountLine> discounts,
            BigDecimal subtotal,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal taxableBase,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal shippingCost,
            BigDecimal total,
            String currency,
            int totalUnits,
            boolean randomOrder,
            boolean blocked,
            boolean capped,
            boolean promotionActive) {

        public static CartView empty(String currency) {
            var cero = BigDecimal.ZERO;
            return new CartView(List.of(), List.of(), List.of(), cero, cero, cero, cero, cero, cero,
                    cero, cero, currency, 0, false, false, false, false);
        }
    }
}
