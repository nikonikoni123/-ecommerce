package com.ecommerce.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.catalog.Product;
import com.ecommerce.catalog.ProductRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.mail.MailService;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.order.dto.CompanyOrderDtos.ChangeItemsRequest;
import com.ecommerce.order.dto.CompanyOrderDtos.OrderItemChange;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.user.UserType;

/**
 * Cambios de estado y de productos.
 *
 * <p>Se comprueba lo que la especificacion exige de forma tajante —que toda modificacion avise al
 * cliente— y lo que decide cuanto dinero cambia de manos al reeditar un pedido ya pagado.
 */
@ExtendWith(MockitoExtension.class)
class OrderManagementServiceTest {

    private static final String EMPRESA = "empresa-1";

    @Mock private OrderRepository orders;
    @Mock private ProductRepository products;
    @Mock private StockService stockService;
    @Mock private PricingService pricingService;
    @Mock private CompanyOrderService companyOrders;
    @Mock private MailService mailService;
    @Mock private NotificationService notifications;

    private OrderManagementService service;
    private AppPrincipal actor;

    @BeforeEach
    void setUp() {
        service = new OrderManagementService(orders, products, stockService, pricingService,
                companyOrders, mailService, notifications);
        actor = new AppPrincipal("gestor-1", "gestor@test.local", UserType.COMPANY_MEMBER, EMPRESA, false, Set.of());
        lenient().when(pricingService.shippingFor(any())).thenReturn(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------ estado

    @Test
    void avanzarDeEstadoAvisaAlClientePorLosDosCanales() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);

        service.changeStatus(actor, order.getId(), "ALISTANDO_PEDIDO", "Ya lo estamos empaquetando");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ALISTANDO_PEDIDO);
        // La especificacion pide correo Y notificacion en cualquier modificacion.
        verify(notifications).push(eq("cliente-1"), any(), anyString(), anyString(), anyString());
        verify(mailService).sendNotice(eq("cliente@test.local"), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void rechazaUnaTransicionInvalida() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);

        assertThatThrownBy(() -> service.changeStatus(actor, order.getId(), "ENTREGADO", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No se puede pasar");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARANDO_ORDEN);
        verify(orders, never()).save(any());
        verify(notifications, never()).push(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void rechazaQuedarseEnElMismoEstado() {
        var order = pedido(OrderStatus.ENVIANDO);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);

        assertThatThrownBy(() -> service.changeStatus(actor, order.getId(), "ENVIANDO", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ya esta en ese estado");
    }

    @Test
    void cancelarDevuelveElStockDeTodasLasLineas() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);

        service.changeStatus(actor, order.getId(), "CANCELADO", "Sin existencias");

        verify(stockService).release("p1", 2);
        verify(stockService).release("p2", 1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELADO);
    }

    @Test
    void avanzarSinCancelarNoTocaElStock() {
        var order = pedido(OrderStatus.ALISTANDO_PEDIDO);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);

        service.changeStatus(actor, order.getId(), "ENVIANDO", null);

        verify(stockService, never()).release(anyString(), anyInt());
    }

    // ------------------------------------------------------------------ productos

    @Test
    void reducirLaCantidadDevuelveLaDiferenciaAlCatalogo() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "10000")));

        // De 2 unidades de p1 y 1 de p2, se queda solo 1 de p1.
        service.changeItems(actor, order.getId(),
                new ChangeItemsRequest(List.of(new OrderItemChange("p1", 1)), null));

        verify(stockService).release("p1", 1);   // sobra una unidad
        verify(stockService).release("p2", 1);   // la linea entera desaparece
        verify(stockService, never()).reserve(anyString(), anyInt());
    }

    @Test
    void aumentarLaCantidadReservaLasUnidadesNuevas() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "10000")));
        when(products.findById("p2")).thenReturn(Optional.of(producto("p2", "10000")));
        when(stockService.reserve("p1", 3)).thenReturn(true);

        service.changeItems(actor, order.getId(), new ChangeItemsRequest(
                List.of(new OrderItemChange("p1", 5), new OrderItemChange("p2", 1)), null));

        verify(stockService).reserve("p1", 3);
    }

    @Test
    void siFaltaStockNoSeCambiaNadaYSeLiberaLoReservado() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "10000")));
        when(products.findById("p2")).thenReturn(Optional.of(producto("p2", "10000")));
        when(stockService.reserve("p1", 1)).thenReturn(true);
        when(stockService.reserve("p2", 4)).thenReturn(false);

        assertThatThrownBy(() -> service.changeItems(actor, order.getId(), new ChangeItemsRequest(
                List.of(new OrderItemChange("p1", 3), new OrderItemChange("p2", 5)), null)))
                .isInstanceOf(ApiException.class);

        verify(stockService).release("p1", 1);   // se devuelve lo que si se habia reservado
        verify(orders, never()).save(any());
    }

    @Test
    void elRecalculoConservaElPorcentajeDeDescuentoOriginal() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        order.setDiscountPercent(new BigDecimal("10"));
        order.setTaxRate(new BigDecimal("19"));
        order.setDiscounts(List.of(new Order.AppliedDiscount("VENTANA", "Promocion",
                new BigDecimal("10"), new BigDecimal("3000"))));
        when(companyOrders.require(actor, order.getId())).thenReturn(order);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "10000")));

        // Se queda 1 unidad de p1 a 10.000: subtotal 10.000.
        service.changeItems(actor, order.getId(),
                new ChangeItemsRequest(List.of(new OrderItemChange("p1", 1)), null));

        assertThat(order.getSubtotal()).isEqualByComparingTo("10000.00");
        // El 10% original se mantiene aunque la promocion ya no siguiera activa.
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("1000.00");
        assertThat(order.getTaxableBase()).isEqualByComparingTo("9000.00");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1710.00");
        assertThat(order.getTotal()).isEqualByComparingTo("10710.00");
    }

    @Test
    void abaratarElPedidoDejaSaldoAFavorDelCliente() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        order.setTotal(new BigDecimal("30000.00"));
        when(companyOrders.require(actor, order.getId())).thenReturn(order);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "10000")));

        service.changeItems(actor, order.getId(),
                new ChangeItemsRequest(List.of(new OrderItemChange("p1", 1)), null));

        // Queda 1 unidad a 10.000, sin descuento, mas el 19% de IVA: total 11.900.
        assertThat(order.getTotal()).isEqualByComparingTo("11900.00");
        // El saldo es la diferencia con lo que se habia cobrado: 30.000 - 11.900.
        assertThat(order.getAdjustmentBalance()).isEqualByComparingTo("18100.00");
    }

    @Test
    void encarecerElPedidoDejaSaldoEnContra() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        order.setTotal(new BigDecimal("5000.00"));
        when(companyOrders.require(actor, order.getId())).thenReturn(order);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "10000")));
        when(stockService.reserve("p1", 1)).thenReturn(true);

        service.changeItems(actor, order.getId(),
                new ChangeItemsRequest(List.of(new OrderItemChange("p1", 3)), null));

        // 3 x 10.000 = 30.000 mas IVA son 35.700; el pedido encarecio respecto a los 5.000 cobrados.
        assertThat(order.getAdjustmentBalance()).isNegative();
    }

    @Test
    void noSePuedenCambiarProductosDeUnPedidoYaEnviado() {
        var order = pedido(OrderStatus.ENVIANDO);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);

        assertThatThrownBy(() -> service.changeItems(actor, order.getId(),
                new ChangeItemsRequest(List.of(new OrderItemChange("p1", 1)), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ya no admite cambios");
    }

    @Test
    void cambiarProductosTambienAvisaAlCliente() {
        var order = pedido(OrderStatus.PREPARANDO_ORDEN);
        when(companyOrders.require(actor, order.getId())).thenReturn(order);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "10000")));

        service.changeItems(actor, order.getId(),
                new ChangeItemsRequest(List.of(new OrderItemChange("p1", 1)), "Sustituimos un articulo"));

        verify(notifications, times(1)).push(eq("cliente-1"), any(), anyString(), anyString(),
                anyString());
        verify(mailService, times(1)).sendNotice(eq("cliente@test.local"), anyString(), anyString(),
                anyString(), anyString());
    }

    // ------------------------------------------------------------------ apoyo

    private Order pedido(OrderStatus estado) {
        var order = new Order();
        order.setId("order-1");
        order.setNumber("ORD-2026-000001");
        order.setCompanyId(EMPRESA);
        order.setCompanyName("Empresa de prueba");
        order.setCustomerId("cliente-1");
        order.setCustomerEmail("cliente@test.local");
        order.setCustomerName("Laura Gomez");
        order.setCurrency("COP");
        order.setStatus(estado);
        order.setTaxRate(new BigDecimal("19"));
        order.setItems(List.of(
                new Order.OrderItem("p1", "Producto 1", "p1", null, new BigDecimal("10000"), 2,
                        new BigDecimal("20000")),
                new Order.OrderItem("p2", "Producto 2", "p2", null, new BigDecimal("10000"), 1,
                        new BigDecimal("10000"))));
        return order;
    }

    private Product producto(String id, String precio) {
        var p = new Product();
        p.setId(id);
        p.setName("Producto " + id);
        p.setSlug(id);
        p.setPrice(new BigDecimal(precio));
        p.setStock(100);
        p.setActive(true);
        return p;
    }
}
