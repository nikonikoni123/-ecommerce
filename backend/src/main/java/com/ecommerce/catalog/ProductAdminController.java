package com.ecommerce.catalog;

import com.ecommerce.auth.dto.AuthDtos.MessageResponse;
import com.ecommerce.catalog.dto.ProductDtos.CompanyProductView;
import com.ecommerce.catalog.dto.ProductDtos.CreateProductRequest;
import com.ecommerce.catalog.dto.ProductDtos.UpdateProductRequest;
import com.ecommerce.catalog.dto.ProductDtos.UpdateStockRequest;
import com.ecommerce.common.PageResponse;
import com.ecommerce.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de gestion de productos. Cada operacion exige el permiso correspondiente, que root concede
 * a traves de los cargos o de forma individual.
 */
@RestController
@RequestMapping("/api/company/products")
@Tag(name = "Gestion de productos", description = "Alta, modificacion, stock y baja de productos")
public class ProductAdminController {

    private final ProductAdminService service;

    public ProductAdminController(ProductAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Operation(summary = "Listar los productos de la empresa")
    public PageResponse<CompanyProductView> list(@AuthenticationPrincipal AppPrincipal principal,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.list(principal, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Operation(summary = "Ver un producto de la empresa, incluidas sus etiquetas invisibles")
    public CompanyProductView get(@AuthenticationPrincipal AppPrincipal principal,
                                  @PathVariable String id) {
        return service.get(principal, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    @Operation(summary = "Anadir un producto nuevo")
    public CompanyProductView create(@AuthenticationPrincipal AppPrincipal principal,
                                     @Valid @RequestBody CreateProductRequest request) {
        return service.create(principal, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Operation(summary = "Modificar las caracteristicas de un producto")
    public CompanyProductView update(@AuthenticationPrincipal AppPrincipal principal,
                                     @PathVariable String id,
                                     @Valid @RequestBody UpdateProductRequest request) {
        return service.update(principal, id, request);
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAuthority('STOCK_UPDATE')")
    @Operation(summary = "Modificar la cantidad en stock")
    public CompanyProductView updateStock(@AuthenticationPrincipal AppPrincipal principal,
                                          @PathVariable String id,
                                          @Valid @RequestBody UpdateStockRequest request) {
        return service.updateStock(principal, id, request.stock());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    @Operation(summary = "Eliminar un producto")
    public MessageResponse delete(@AuthenticationPrincipal AppPrincipal principal,
                                  @PathVariable String id) {
        service.delete(principal, id);
        return new MessageResponse("Producto eliminado.");
    }
}
