package com.ecommerce.order.dto;

import com.ecommerce.order.Order;
import com.ecommerce.order.dto.OrderDtos.AddressView;
import com.ecommerce.order.dto.OrderDtos.DiscountView;
import com.ecommerce.order.dto.OrderDtos.GiftView;
import com.ecommerce.order.dto.OrderDtos.OrderDetail;
import com.ecommerce.order.dto.OrderDtos.OrderItemView;
import com.ecommerce.order.dto.OrderDtos.OrderSummary;
import com.ecommerce.order.dto.OrderDtos.PaymentView;
import com.ecommerce.order.dto.OrderDtos.StatusChangeView;
import java.util.List;

/** Conversion de los documentos de pedido a las vistas que consume el frontend. */
public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderSummary toSummary(Order order) {
        return new OrderSummary(
                order.getId(),
                order.getNumber(),
                order.getCompanyId(),
                order.getCompanyName(),
                order.getStatus().name(),
                order.getStatus().getLabel(),
                order.getStatus().isTerminal(),
                order.getItems().stream().mapToInt(Order.OrderItem::getQuantity).sum(),
                order.getTotal(),
                order.getCurrency(),
                order.isRandomOrder(),
                order.getGift() != null && order.getGift().isGift(),
                order.getCreatedAt());
    }

    public static OrderDetail toDetail(Order order) {
        return new OrderDetail(
                order.getId(),
                order.getNumber(),
                order.getCompanyId(),
                order.getCompanyName(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getItems().stream().map(OrderMapper::toItem).toList(),
                order.getSubtotal(),
                order.getDiscounts().stream()
                        .map(d -> new DiscountView(d.getCode(), d.getLabel(), d.getPercent(),
                                d.getAmount()))
                        .toList(),
                order.getDiscountPercent(),
                order.getDiscountAmount(),
                order.getTaxableBase(),
                order.getTaxRate(),
                order.getTaxAmount(),
                order.getShippingCost(),
                order.getTotal(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getStatus().getLabel(),
                order.getStatus().isTerminal(),
                order.getStatusHistory().stream()
                        .map(h -> new StatusChangeView(h.getStatus().name(),
                                h.getStatus().getLabel(), h.getAt(), h.getNote()))
                        .toList(),
                order.isRandomOrder(),
                toAddress(order.getShipping()),
                toGift(order.getGift()),
                toPayment(order.getPayment()),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public static OrderItemView toItem(Order.OrderItem item) {
        return new OrderItemView(item.getProductId(), item.getName(), item.getSlug(),
                item.getImageUrl(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
    }

    public static List<OrderItemView> toItems(List<Order.OrderItem> items) {
        return items.stream().map(OrderMapper::toItem).toList();
    }

    private static AddressView toAddress(Order.Address address) {
        return address == null ? null : new AddressView(address.getRecipientName(),
                address.getAddress(), address.getPostalCode(), address.getPhone());
    }

    private static GiftView toGift(Order.Gift gift) {
        return gift == null ? null : new GiftView(gift.isGift(), gift.getRecipientName(),
                gift.getAddress(), gift.getPostalCode(), gift.getMessage());
    }

    private static PaymentView toPayment(Order.Payment payment) {
        return payment == null ? null : new PaymentView(payment.isSimulated(), payment.getMethod(),
                payment.getReference(), payment.getPaidAt());
    }
}
