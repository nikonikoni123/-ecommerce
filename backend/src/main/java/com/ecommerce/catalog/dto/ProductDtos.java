package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    /**
     * Vista publica del producto. No incluye {@code hiddenTags}: las etiquetas invisibles solo
     * alimentan el ordenamiento del catalogo y nunca deben llegar al navegador.
     */
    public record ProductSummary(
            String id,
            String slug,
            String name,
            String companyId,
            String companyName,
            BigDecimal price,
            String currency,
            boolean inStock,
            List<String> images,
            List<String> categories,
            List<String> tags) {

        public static ProductSummary from(Product p) {
            return new ProductSummary(p.getId(), p.getSlug(), p.getName(), p.getCompanyId(),
                    p.getCompanyName(), p.getPrice(), p.getCurrency(), p.isInStock(),
                    p.getImages(), p.getCategories(), p.getVisibleTags());
        }
    }

    public record ProductDetail(
            String id,
            String slug,
            String name,
            String description,
            String companyId,
            String companyName,
            BigDecimal price,
            String currency,
            int stock,
            boolean inStock,
            List<String> images,
            List<String> categories,
            List<String> tags) {

        public static ProductDetail from(Product p) {
            return new ProductDetail(p.getId(), p.getSlug(), p.getName(), p.getDescription(),
                    p.getCompanyId(), p.getCompanyName(), p.getPrice(), p.getCurrency(),
                    p.getStock(), p.isInStock(), p.getImages(), p.getCategories(),
                    p.getVisibleTags());
        }
    }

    /** Vista para el panel de gestion: aqui si se muestran las etiquetas invisibles a la empresa. */
    public record CompanyProductView(
            String id,
            String slug,
            String name,
            String description,
            BigDecimal price,
            String currency,
            int stock,
            boolean active,
            List<String> images,
            List<String> categories,
            List<String> visibleTags,
            List<String> hiddenTags,
            Instant createdAt,
            Instant updatedAt) {

        public static CompanyProductView from(Product p) {
            return new CompanyProductView(p.getId(), p.getSlug(), p.getName(), p.getDescription(),
                    p.getPrice(), p.getCurrency(), p.getStock(), p.isActive(), p.getImages(),
                    p.getCategories(), p.getVisibleTags(), p.getHiddenTags(), p.getCreatedAt(),
                    p.getUpdatedAt());
        }
    }

    public record CreateProductRequest(
            @NotBlank(message = "El nombre del producto es obligatorio")
            @Size(max = 150) String name,

            @NotBlank(message = "La descripcion es obligatoria")
            @Size(max = 5000) String description,

            @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor que cero")
            BigDecimal price,

            String currency,

            @NotNull(message = "La cantidad en stock es obligatoria")
            @Min(value = 0, message = "El stock no puede ser negativo") Integer stock,

            List<String> images,
            List<String> categories,
            List<String> visibleTags,

            /** Etiquetas invisibles de afinidad. Opcionales. */
            List<String> hiddenTags,

            Boolean active) {
    }

    /** Modificacion parcial: solo se aplican los campos presentes. */
    public record UpdateProductRequest(
            @Size(min = 1, max = 150, message = "El nombre no puede quedar vacio") String name,
            @Size(min = 1, max = 5000, message = "La descripcion no puede quedar vacia") String description,
            @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor que cero")
            BigDecimal price,
            String currency,
            @Min(value = 0, message = "El stock no puede ser negativo") Integer stock,
            List<String> images,
            List<String> categories,
            List<String> visibleTags,
            List<String> hiddenTags,
            Boolean active) {
    }

    public record UpdateStockRequest(
            @NotNull(message = "La cantidad es obligatoria")
            @Min(value = 0, message = "El stock no puede ser negativo") Integer stock) {
    }

    /** Filtros del catalogo publico. */
    public record CatalogQuery(
            String search,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStockOnly,
            String sort) {
    }
}
