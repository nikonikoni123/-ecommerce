package com.ecommerce.order;

import com.ecommerce.common.PageResponse;
import com.ecommerce.order.dto.CompanyOrderDtos.ChangeItemsRequest;
import com.ecommerce.order.dto.CompanyOrderDtos.ChangeStatusRequest;
import com.ecommerce.order.dto.CompanyOrderDtos.CompanyOrderRow;
import com.ecommerce.order.dto.CompanyOrderDtos.CompanyOrderStats;
import com.ecommerce.order.dto.CompanyOrderDtos.RefundView;
import com.ecommerce.order.dto.CompanyOrderDtos.ResolveRefundRequest;
import com.ecommerce.order.dto.OrderDtos.OrderDetail;
import com.ecommerce.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de pedidos de la empresa.
 *
 * <p>Cada operacion exige su permiso: ver, cambiar estado, cambiar productos y gestionar
 * reembolsos son competencias separables, de modo que root pueda repartirlas entre cargos.
 */
@RestController
@RequestMapping("/api/company")
@Tag(name = "Gestion de pedidos", description = "Panel de pedidos, estados y reembolsos")
public class CompanyOrderController {

    private final CompanyOrderService companyOrders;
    private final OrderManagementService management;
    private final RefundService refundService;

    public CompanyOrderController(CompanyOrderService companyOrders,
                                  OrderManagementService management, RefundService refundService) {
        this.companyOrders = companyOrders;
        this.management = management;
        this.refundService = refundService;
    }

    // ---------------------------------------------------------------- listado

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    @Operation(summary = "Listar pedidos por prioridad, fecha, vencimiento, cantidad o estado")
    public PageResponse<CompanyOrderRow> list(
            @AuthenticationPrincipal AppPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean onlyOverdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return companyOrders.list(principal, status, sort, onlyOverdue,
                Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/orders/stats")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    @Operation(summary = "Contadores del panel: en curso, vencidos, entregados y reembolsos")
    public CompanyOrderStats stats(@AuthenticationPrincipal AppPrincipal principal) {
        return companyOrders.stats(principal);
    }

    @GetMapping("/orders/statuses")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    @Operation(summary = "Estados disponibles y sus transiciones permitidas")
    public List<StatusOption> statuses() {
        return Arrays.stream(OrderStatus.values())
                .map(s -> new StatusOption(s.name(), s.getLabel(), s.isTerminal(),
                        s.allowedTransitions().stream().map(Enum::name).sorted().toList()))
                .toList();
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    @Operation(summary = "Detalle de un pedido de la empresa")
    public OrderDetail detail(@AuthenticationPrincipal AppPrincipal principal,
                              @PathVariable String id) {
        return companyOrders.detail(principal, id);
    }

    // ---------------------------------------------------------------- cambios

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasAuthority('ORDER_STATUS_CHANGE')")
    @Operation(summary = "Cambiar el estado del pedido y avisar al cliente")
    public OrderDetail changeStatus(@AuthenticationPrincipal AppPrincipal principal,
                                    @PathVariable String id,
                                    @Valid @RequestBody ChangeStatusRequest request) {
        return management.changeStatus(principal, id, request.status(), request.note());
    }

    @PutMapping("/orders/{id}/items")
    @PreAuthorize("hasAuthority('ORDER_ITEMS_CHANGE')")
    @Operation(summary = "Cambiar los productos del pedido, ajustando stock e importes")
    public OrderDetail changeItems(@AuthenticationPrincipal AppPrincipal principal,
                                   @PathVariable String id,
                                   @Valid @RequestBody ChangeItemsRequest request) {
        return management.changeItems(principal, id, request);
    }

    // ---------------------------------------------------------------- reembolsos

    @GetMapping("/refunds")
    @PreAuthorize("hasAuthority('REFUND_MANAGE')")
    @Operation(summary = "Solicitudes de reembolso recibidas")
    public PageResponse<RefundView> refunds(@AuthenticationPrincipal AppPrincipal principal,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return refundService.list(principal, status, Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
    }

    @PostMapping("/refunds/{id}/resolve")
    @PreAuthorize("hasAuthority('REFUND_MANAGE')")
    @Operation(summary = "Aprobar o rechazar una solicitud de reembolso")
    public RefundView resolve(@AuthenticationPrincipal AppPrincipal principal,
                              @PathVariable String id,
                              @Valid @RequestBody ResolveRefundRequest request) {
        return refundService.resolve(principal, id, request.approve(), request.resolution());
    }

    public record StatusOption(String status, String label, boolean terminal,
                               List<String> allowedTransitions) {
    }
}
