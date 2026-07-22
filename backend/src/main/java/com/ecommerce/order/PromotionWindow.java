package com.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Rango de tiempo parametrizado durante el cual se aplican los descuentos.
 *
 * <p>La especificacion condiciona todos los descuentos a que la orden quede registrada dentro de la
 * ventana, asi que fuera de ella no se aplica ninguno. Vive en Mongo, y no en la configuracion, para
 * poder abrir o cerrar promociones sin reiniciar la aplicacion.
 */
@Document("promotionWindows")
public class PromotionWindow {

    @Id
    private String id;

    private String name;

    private Instant startsAt;
    private Instant endsAt;

    /** Descuento aplicado a toda orden registrada dentro de la ventana. */
    private BigDecimal orderDiscountPercent;

    /** Descuento adicional cuando el pedido nace de la caja sorpresa. */
    private BigDecimal randomOrderDiscountPercent;

    private boolean active = true;

    private Instant createdAt = Instant.now();

    public boolean coversNow(Instant when) {
        return active && !when.isBefore(startsAt) && !when.isAfter(endsAt);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public BigDecimal getOrderDiscountPercent() {
        return orderDiscountPercent;
    }

    public void setOrderDiscountPercent(BigDecimal orderDiscountPercent) {
        this.orderDiscountPercent = orderDiscountPercent;
    }

    public BigDecimal getRandomOrderDiscountPercent() {
        return randomOrderDiscountPercent;
    }

    public void setRandomOrderDiscountPercent(BigDecimal randomOrderDiscountPercent) {
        this.randomOrderDiscountPercent = randomOrderDiscountPercent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
