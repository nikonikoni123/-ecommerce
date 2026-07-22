package com.ecommerce.cart;

import com.ecommerce.auth.dto.AuthDtos.MessageResponse;
import com.ecommerce.cart.dto.CartDtos.AddItemRequest;
import com.ecommerce.cart.dto.CartDtos.CartView;
import com.ecommerce.cart.dto.CartDtos.ReplaceItemRequest;
import com.ecommerce.cart.dto.CartDtos.UpdateQuantityRequest;
import com.ecommerce.common.ApiException;
import com.ecommerce.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Carrito", description = "Carrito de compra del cliente")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Ver el carrito con precios actuales y totales")
    public CartView view(@AuthenticationPrincipal AppPrincipal principal) {
        return cartService.view(customer(principal), false);
    }

    @GetMapping("/quote")
    @Operation(summary = "Cotizar el carrito, opcionalmente como pedido sorpresa")
    public CartView quote(@AuthenticationPrincipal AppPrincipal principal,
                          @RequestParam(defaultValue = "false") boolean randomOrder) {
        return cartService.view(customer(principal), randomOrder);
    }

    @GetMapping("/count")
    @Operation(summary = "Numero de articulos, para el indicador del encabezado")
    public ItemCount count(@AuthenticationPrincipal AppPrincipal principal) {
        return new ItemCount(cartService.totalUnits(customer(principal)));
    }

    @PostMapping("/items")
    @Operation(summary = "Anadir un producto al carrito")
    public CartView addItem(@AuthenticationPrincipal AppPrincipal principal,
                            @Valid @RequestBody AddItemRequest request) {
        return cartService.addItem(customer(principal), request.productId(), request.quantity());
    }

    @PatchMapping("/items/{productId}")
    @Operation(summary = "Modificar la cantidad de un producto del carrito")
    public CartView updateQuantity(@AuthenticationPrincipal AppPrincipal principal,
                                   @PathVariable String productId,
                                   @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.updateQuantity(customer(principal), productId, request.quantity());
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Sustituir un producto del carrito por otro")
    public CartView replaceItem(@AuthenticationPrincipal AppPrincipal principal,
                                @PathVariable String productId,
                                @Valid @RequestBody ReplaceItemRequest request) {
        return cartService.replaceItem(customer(principal), productId, request.newProductId(),
                request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Quitar un producto del carrito")
    public CartView removeItem(@AuthenticationPrincipal AppPrincipal principal,
                               @PathVariable String productId) {
        return cartService.removeItem(customer(principal), productId);
    }

    @DeleteMapping
    @Operation(summary = "Vaciar el carrito")
    public MessageResponse clear(@AuthenticationPrincipal AppPrincipal principal) {
        cartService.clear(customer(principal));
        return new MessageResponse("Carrito vaciado.");
    }

    /** El carrito es exclusivo de las cuentas de comprador. */
    private String customer(AppPrincipal principal) {
        if (!principal.isCustomer()) {
            throw ApiException.forbidden("NOT_A_CUSTOMER",
                    "El carrito solo esta disponible para las cuentas de usuario comprador.");
        }
        return principal.userId();
    }

    public record ItemCount(int count) {
    }
}
