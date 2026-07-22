package com.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pedido de un cliente a <b>una sola empresa</b>.
 *
 * <p>Un carrito con productos de varias empresas se divide en varios pedidos al pagar, cada uno con
 * su numero, su estado y su factura. Asi cada empresa gestiona lo suyo sin pisar el estado de otra,
 * que es lo que necesita el panel de la Etapa 3.
 *
 * <p>Los importes se guardan calculados. Una factura ya emitida no puede cambiar porque alguien
 * modifique el precio del producto o la ventana de promociones.
 */
@Document("orders")
public class Order {

    @Id
    private String id;

    /** Numero legible y unico, del estilo ORD-2026-000123. */
    private String number;

    private String customerId;
    private String customerEmail;
    private String customerName;

    private String companyId;
    private String companyName;

    private List<OrderItem> items = new ArrayList<>();

    private BigDecimal subtotal = BigDecimal.ZERO;

    /** Desglose de cada descuento aplicado, para poder justificarlo en la factura. */
    private List<AppliedDiscount> discounts = new ArrayList<>();

    private BigDecimal discountPercent = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private BigDecimal taxableBase = BigDecimal.ZERO;
    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;

    private BigDecimal shippingCost = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    private String currency = "COP";

    private OrderStatus status = OrderStatus.PREPARANDO_ORDEN;
    private List<StatusChange> statusHistory = new ArrayList<>();

    /** El pedido nacio de la funcion de caja sorpresa. */
    private boolean randomOrder;

    private Address shipping;
    private Gift gift;
    private Payment payment;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    /** Direccion a la que se entrega: la del regalo si lo es, y si no la del comprador. */
    public Address deliveryAddress() {
        if (gift != null && gift.isGift() && gift.getAddress() != null) {
            return new Address(gift.getRecipientName(), gift.getAddress(), gift.getPostalCode(), null);
        }
        return shipping;
    }

    public void pushStatus(OrderStatus newStatus, String byUserId, String note) {
        this.status = newStatus;
        this.statusHistory.add(new StatusChange(newStatus, Instant.now(), byUserId, note));
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------ tipos anidados

    public static class OrderItem {
        private String productId;
        private String name;
        private String slug;
        private String imageUrl;
        private BigDecimal unitPrice;
        private int quantity;
        private BigDecimal lineTotal;

        public OrderItem() {
        }

        public OrderItem(String productId, String name, String slug, String imageUrl,
                         BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
            this.productId = productId;
            this.name = name;
            this.slug = slug;
            this.imageUrl = imageUrl;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.lineTotal = lineTotal;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getLineTotal() {
            return lineTotal;
        }

        public void setLineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
        }
    }

    public static class AppliedDiscount {
        private String code;
        private String label;
        private BigDecimal percent;
        private BigDecimal amount;

        public AppliedDiscount() {
        }

        public AppliedDiscount(String code, String label, BigDecimal percent, BigDecimal amount) {
            this.code = code;
            this.label = label;
            this.percent = percent;
            this.amount = amount;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public BigDecimal getPercent() {
            return percent;
        }

        public void setPercent(BigDecimal percent) {
            this.percent = percent;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    public static class StatusChange {
        private OrderStatus status;
        private Instant at;
        private String byUserId;
        private String note;

        public StatusChange() {
        }

        public StatusChange(OrderStatus status, Instant at, String byUserId, String note) {
            this.status = status;
            this.at = at;
            this.byUserId = byUserId;
            this.note = note;
        }

        public OrderStatus getStatus() {
            return status;
        }

        public void setStatus(OrderStatus status) {
            this.status = status;
        }

        public Instant getAt() {
            return at;
        }

        public void setAt(Instant at) {
            this.at = at;
        }

        public String getByUserId() {
            return byUserId;
        }

        public void setByUserId(String byUserId) {
            this.byUserId = byUserId;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    public static class Address {
        private String recipientName;
        private String address;
        private String postalCode;
        private String phone;

        public Address() {
        }

        public Address(String recipientName, String address, String postalCode, String phone) {
            this.recipientName = recipientName;
            this.address = address;
            this.postalCode = postalCode;
            this.phone = phone;
        }

        public String getRecipientName() {
            return recipientName;
        }

        public void setRecipientName(String recipientName) {
            this.recipientName = recipientName;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    /** Envio como regalo: destinatario, direccion y codigo postal distintos de los del comprador. */
    public static class Gift {
        private boolean isGift;
        private String recipientName;
        private String address;
        private String postalCode;
        private String message;

        public boolean isGift() {
            return isGift;
        }

        public void setGift(boolean gift) {
            isGift = gift;
        }

        public String getRecipientName() {
            return recipientName;
        }

        public void setRecipientName(String recipientName) {
            this.recipientName = recipientName;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /** Pago simulado: no hay pasarela real, pero se conserva la referencia y la fecha. */
    public static class Payment {
        private boolean simulated = true;
        private String method;
        private String reference;
        private Instant paidAt;

        public Payment() {
        }

        public Payment(String method, String reference, Instant paidAt) {
            this.method = method;
            this.reference = reference;
            this.paidAt = paidAt;
        }

        public boolean isSimulated() {
            return simulated;
        }

        public void setSimulated(boolean simulated) {
            this.simulated = simulated;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public Instant getPaidAt() {
            return paidAt;
        }

        public void setPaidAt(Instant paidAt) {
            this.paidAt = paidAt;
        }
    }

    // ------------------------------------------------------------------ accesores

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public List<AppliedDiscount> getDiscounts() {
        return discounts;
    }

    public void setDiscounts(List<AppliedDiscount> discounts) {
        this.discounts = discounts == null ? new ArrayList<>() : discounts;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(BigDecimal discountPercent) {
        this.discountPercent = discountPercent;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTaxableBase() {
        return taxableBase;
    }

    public void setTaxableBase(BigDecimal taxableBase) {
        this.taxableBase = taxableBase;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<StatusChange> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<StatusChange> statusHistory) {
        this.statusHistory = statusHistory == null ? new ArrayList<>() : statusHistory;
    }

    public boolean isRandomOrder() {
        return randomOrder;
    }

    public void setRandomOrder(boolean randomOrder) {
        this.randomOrder = randomOrder;
    }

    public Address getShipping() {
        return shipping;
    }

    public void setShipping(Address shipping) {
        this.shipping = shipping;
    }

    public Gift getGift() {
        return gift;
    }

    public void setGift(Gift gift) {
        this.gift = gift;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
