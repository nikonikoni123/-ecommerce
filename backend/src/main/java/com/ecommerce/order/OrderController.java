package com.ecommerce.order;

import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.order.dto.OrderDtos.CheckoutRequest;
import com.ecommerce.order.dto.OrderDtos.CheckoutResponse;
import com.ecommerce.order.dto.OrderDtos.OrderDetail;
import com.ecommerce.order.dto.OrderDtos.OrderSummary;
import com.ecommerce.order.dto.OrderDtos.SurpriseProposal;
import com.ecommerce.order.dto.OrderDtos.SurpriseRequest;
import com.ecommerce.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Pedidos", description = "Pago simulado, caja sorpresa, pedidos y facturas")
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final SurpriseBoxService surpriseBoxService;
    private final InvoiceService invoiceService;

    public OrderController(CheckoutService checkoutService, OrderService orderService,
                           SurpriseBoxService surpriseBoxService, InvoiceService invoiceService) {
        this.checkoutService = checkoutService;
        this.orderService = orderService;
        this.surpriseBoxService = surpriseBoxService;
        this.invoiceService = invoiceService;
    }

    @PostMapping("/checkout")
    @Operation(summary = "Pagar el carrito. Genera un pedido por cada empresa vendedora")
    public CheckoutResponse checkout(@AuthenticationPrincipal AppPrincipal principal,
                                     @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(customer(principal), request);
    }

    @PostMapping("/orders/surprise/preview")
    @Operation(summary = "Proponer una caja sorpresa, sin comprometer la compra")
    public SurpriseProposal surprise(@AuthenticationPrincipal AppPrincipal principal,
                                     @Valid @RequestBody SurpriseRequest request) {
        return surpriseBoxService.propose(customer(principal), request);
    }

    @GetMapping("/orders")
    @Operation(summary = "Mis pedidos: en proceso, historial o todos")
    public PageResponse<OrderSummary> list(@AuthenticationPrincipal AppPrincipal principal,
                                           @RequestParam(required = false) String scope,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return orderService.list(customer(principal), scope, Math.max(page, 0),
                Math.min(Math.max(size, 1), 50));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Detalle del pedido y estado de entrega")
    public OrderDetail detail(@AuthenticationPrincipal AppPrincipal principal,
                              @PathVariable String id) {
        return orderService.detail(customer(principal), id);
    }

    @GetMapping("/orders/{id}/invoice")
    @Operation(summary = "Descargar la factura del pedido en PDF")
    public ResponseEntity<ByteArrayResource> invoice(@AuthenticationPrincipal AppPrincipal principal,
                                                     @PathVariable String id) {
        var order = orderService.require(customer(principal), id);
        byte[] pdf = invoiceService.render(order);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"factura-%s.pdf\"".formatted(order.getNumber()))
                .contentLength(pdf.length)
                .body(new ByteArrayResource(pdf));
    }

    private String customer(AppPrincipal principal) {
        if (!principal.isCustomer()) {
            throw ApiException.forbidden("NOT_A_CUSTOMER",
                    "Esta seccion es exclusiva de las cuentas de usuario comprador.");
        }
        return principal.userId();
    }
}
