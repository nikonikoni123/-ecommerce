package com.ecommerce.catalog;

import com.ecommerce.catalog.dto.ProductDtos.CatalogQuery;
import com.ecommerce.catalog.dto.ProductDtos.ProductDetail;
import com.ecommerce.catalog.dto.ProductDtos.ProductSummary;
import com.ecommerce.common.PageResponse;
import com.ecommerce.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo publico. Es accesible sin sesion, pero si hay un cliente autenticado el orden de los
 * productos se personaliza con su afinidad.
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Catalogo", description = "Consulta publica de productos")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "Listar productos con filtros, paginacion y priorizacion personalizada")
    public PageResponse<ProductSummary> browse(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStockOnly,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal AppPrincipal principal) {

        var filters = new CatalogQuery(search, category, minPrice, maxPrice, inStockOnly, sort);
        return catalogService.browse(filters, Math.max(page, 0), Math.min(Math.max(size, 1), 48),
                principal);
    }

    @GetMapping("/categories")
    @Operation(summary = "Categorias disponibles para los filtros del catalogo")
    public List<String> categories() {
        return catalogService.categories();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Ver el detalle de un producto")
    public ProductDetail detail(@PathVariable String slug,
                                @AuthenticationPrincipal AppPrincipal principal) {
        return catalogService.detail(slug, principal);
    }
}
