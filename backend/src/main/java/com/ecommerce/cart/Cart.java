package com.ecommerce.cart;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Carrito persistente, uno por cliente.
 *
 * <p>Solo guarda el identificador del producto y la cantidad. El nombre, el precio y el stock se
 * leen del catalogo en cada consulta, de modo que una subida de precio o una rotura de stock se
 * reflejan al instante. La foto de los precios se toma al crear el pedido, no aqui.
 */
@Document("carts")
public class Cart {

    @Id
    private String id;

    /** Unico por cliente. */
    private String customerId;

    private List<CartItem> items = new ArrayList<>();

    /**
     * El contenido procede de una caja sorpresa aceptada tal cual.
     *
     * <p>Se guarda aqui, y no se recibe al pagar, por dos motivos: el descuento debe verse en el
     * carrito desde que se acepta la caja, y el cliente no puede concederselo el mismo enviando la
     * marca en la peticion de pago. Cualquier edicion manual lo desactiva, porque el descuento se
     * concede a cambio de aceptar la seleccion de la plataforma, no de rehacerla.
     */
    private boolean randomOrder;

    private Instant updatedAt = Instant.now();

    public Cart() {
    }

    public Cart(String customerId) {
        this.customerId = customerId;
    }

    public Optional<CartItem> findItem(String productId) {
        return items.stream().filter(i -> i.getProductId().equals(productId)).findFirst();
    }

    public void removeItem(String productId) {
        items.removeIf(i -> i.getProductId().equals(productId));
    }

    public int totalUnits() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public boolean isRandomOrder() {
        return randomOrder;
    }

    public void setRandomOrder(boolean randomOrder) {
        this.randomOrder = randomOrder;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Linea del carrito. Deliberadamente minima: sin precio ni nombre. */
    public static class CartItem {

        private String productId;
        private int quantity;
        private Instant addedAt = Instant.now();

        public CartItem() {
        }

        public CartItem(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public Instant getAddedAt() {
            return addedAt;
        }

        public void setAddedAt(Instant addedAt) {
            this.addedAt = addedAt;
        }
    }
}
