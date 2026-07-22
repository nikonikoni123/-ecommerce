package com.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.cart.Cart;
import com.ecommerce.cart.CartService;
import com.ecommerce.catalog.Product;
import com.ecommerce.catalog.ProductRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.common.SequenceService;
import com.ecommerce.config.AppProperties;
import com.ecommerce.mail.MailService;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.order.dto.OrderDtos.CheckoutRequest;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas del pago.
 *
 * <p>Cubren las dos cosas que la auditoria dejo sin verificar: que el stock reservado se devuelve si
 * el pago falla a medias, y que la condicion de pedido sorpresa la aporta el carrito y no la
 * peticion, que es justo por donde se perdia el descuento del 50%.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    private static final String CLIENTE = "cliente-1";

    @Mock private CartService cartService;
    @Mock private ProductRepository products;
    @Mock private OrderRepository orders;
    @Mock private UserRepository users;
    @Mock private PricingService pricingService;
    @Mock private StockService stockService;
    @Mock private SequenceService sequences;
    @Mock private MailService mailService;
    @Mock private NotificationService notifications;

    private CheckoutService service;

    @BeforeEach
    void setUp() {
        var pricing = new AppProperties.Pricing(new BigDecimal("19"), new BigDecimal("15000"),
                new BigDecimal("200000"), 3, new BigDecimal("5"), new BigDecimal("65"));
        var properties = new AppProperties("http://localhost:8080", "http://localhost:4200",
                "no-reply@test", false, null, null, pricing, new AppProperties.Orders(5, 30, 24));

        service = new CheckoutService(cartService, products, orders, users, pricingService,
                stockService, sequences, mailService, notifications, properties);

        var cliente = new User();
        cliente.setId(CLIENTE);
        cliente.setType(UserType.CUSTOMER);
        cliente.setEmail("cliente@test.local");
        cliente.setFirstName("Laura");
        cliente.setLastName("Gomez");
        lenient().when(users.findById(CLIENTE)).thenReturn(Optional.of(cliente));

        lenient().when(sequences.nextOrderNumber()).thenReturn("ORD-2026-000001");
        lenient().when(pricingService.quote(any(), anyString(), anyBoolean(), any()))
                .thenReturn(quoteVacia());
    }

    @Test
    void devuelveElStockReservadoSiFallaUnaLineaPosterior() {
        var cart = carrito(false, linea("p1", 2), linea("p2", 1));
        when(cartService.load(CLIENTE)).thenReturn(cart);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "c1")));
        when(products.findById("p2")).thenReturn(Optional.of(producto("p2", "c1")));

        // La primera reserva funciona y la segunda no: el escenario donde hay que compensar.
        when(stockService.reserve("p1", 2)).thenReturn(true);
        when(stockService.reserve("p2", 1)).thenReturn(false);

        assertThatThrownBy(() -> service.checkout(CLIENTE, peticion()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("agotaron");

        // Lo unico que se llego a reservar tiene que volver al catalogo.
        verify(stockService).release("p1", 2);
        verify(stockService, never()).release(eq("p2"), anyInt());
        // Y no puede quedar ningun pedido a medias.
        verify(orders, never()).saveAll(any());
    }

    @Test
    void noDejaStockRetenidoCuandoLaPrimeraReservaYaFalla() {
        var cart = carrito(false, linea("p1", 1));
        when(cartService.load(CLIENTE)).thenReturn(cart);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "c1")));
        when(stockService.reserve("p1", 1)).thenReturn(false);

        assertThatThrownBy(() -> service.checkout(CLIENTE, peticion()))
                .isInstanceOf(ApiException.class);

        verify(stockService, never()).release(anyString(), anyInt());
    }

    @Test
    void laCondicionDeSorpresaSaleDelCarritoNoDeLaPeticion() {
        // Este es el fallo que se colo: el carrito manda, y la peticion ni siquiera lo puede pedir.
        var cart = carrito(true, linea("p1", 1));
        when(cartService.load(CLIENTE)).thenReturn(cart);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "c1")));
        when(stockService.reserve("p1", 1)).thenReturn(true);

        service.checkout(CLIENTE, peticion());

        verify(pricingService).quote(any(), eq(CLIENTE), eq(true), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Order>> captor = ArgumentCaptor.forClass(List.class);
        verify(orders).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(o -> assertThat(o.isRandomOrder()).isTrue());
    }

    @Test
    void unCarritoNormalNoRecibeElDescuentoDeSorpresa() {
        var cart = carrito(false, linea("p1", 1));
        when(cartService.load(CLIENTE)).thenReturn(cart);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "c1")));
        when(stockService.reserve("p1", 1)).thenReturn(true);

        service.checkout(CLIENTE, peticion());

        verify(pricingService).quote(any(), eq(CLIENTE), eq(false), any());
    }

    @Test
    void dividElCarritoEnUnPedidoPorEmpresa() {
        var cart = carrito(false, linea("p1", 1), linea("p2", 1));
        when(cartService.load(CLIENTE)).thenReturn(cart);
        when(products.findById("p1")).thenReturn(Optional.of(producto("p1", "empresa-A")));
        when(products.findById("p2")).thenReturn(Optional.of(producto("p2", "empresa-B")));
        when(stockService.reserve(anyString(), anyInt())).thenReturn(true);

        var response = service.checkout(CLIENTE, peticion());

        assertThat(response.orders()).hasSize(2);
        // Un unico pago para todos los pedidos.
        assertThat(response.paymentReference()).isNotBlank();
    }

    @Test
    void rechazaElPagoConElCarritoVacio() {
        when(cartService.load(CLIENTE)).thenReturn(new Cart(CLIENTE));

        assertThatThrownBy(() -> service.checkout(CLIENTE, peticion()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("vacio");
    }

    @Test
    void exigeLosDatosDelRegaloSiSeMarcaComoRegalo() {
        when(cartService.load(CLIENTE)).thenReturn(carrito(false, linea("p1", 1)));

        var request = new CheckoutRequest("Laura", "Calle 1", "110111", "300", "Tarjeta",
                true, null);

        assertThatThrownBy(() -> service.checkout(CLIENTE, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("regalo");
    }

    // ------------------------------------------------------------------ apoyo

    private CheckoutRequest peticion() {
        return new CheckoutRequest("Laura", "Calle 1", "110111", "300555", "Tarjeta", false, null);
    }

    private Cart carrito(boolean sorpresa, Cart.CartItem... items) {
        var cart = new Cart(CLIENTE);
        cart.setItems(List.of(items));
        cart.setRandomOrder(sorpresa);
        return cart;
    }

    private Cart.CartItem linea(String productId, int cantidad) {
        return new Cart.CartItem(productId, cantidad);
    }

    private Product producto(String id, String companyId) {
        var p = new Product();
        p.setId(id);
        p.setName("Producto " + id);
        p.setSlug(id);
        p.setCompanyId(companyId);
        p.setCompanyName("Empresa " + companyId);
        p.setPrice(new BigDecimal("10000"));
        p.setStock(100);
        p.setActive(true);
        return p;
    }

    private PricingService.Quote quoteVacia() {
        var cero = BigDecimal.ZERO;
        return new PricingService.Quote(cero,
                new DiscountService.DiscountResult(cero, cero, List.of(), false),
                cero, new BigDecimal("19"), cero, cero, cero);
    }
}
