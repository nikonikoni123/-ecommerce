package com.ecommerce.catalog;

import com.ecommerce.activity.ActivityService;
import com.ecommerce.catalog.dto.ProductDtos.CompanyProductView;
import com.ecommerce.catalog.dto.ProductDtos.CreateProductRequest;
import com.ecommerce.catalog.dto.ProductDtos.UpdateProductRequest;
import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.company.CompanyRepository;
import com.ecommerce.security.AppPrincipal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Panel de gestion de productos de una empresa: alta, modificacion, stock y baja. */
@Service
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final ActivityService activityService;

    public ProductAdminService(ProductRepository productRepository,
                               CompanyRepository companyRepository,
                               ActivityService activityService) {
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.activityService = activityService;
    }

    public PageResponse<CompanyProductView> list(AppPrincipal principal, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(
                productRepository.findByCompanyId(companyOf(principal), pageable),
                CompanyProductView::from);
    }

    public CompanyProductView get(AppPrincipal principal, String id) {
        return CompanyProductView.from(require(principal, id));
    }

    public CompanyProductView create(AppPrincipal principal, CreateProductRequest request) {
        String companyId = companyOf(principal);
        String companyName = companyRepository.findById(companyId)
                .map(company -> company.getName())
                .orElseThrow(() -> ApiException.notFound("La empresa no existe."));

        var product = new Product();
        product.setCompanyId(companyId);
        product.setCompanyName(companyName);
        product.setName(request.name().trim());
        product.setSlug(uniqueSlug(request.name()));
        product.setDescription(request.description().trim());
        product.setPrice(request.price());
        if (request.currency() != null && !request.currency().isBlank()) {
            product.setCurrency(request.currency().trim().toUpperCase());
        }
        product.setStock(request.stock());
        product.setImages(request.images());
        product.setCategories(normalize(request.categories()));
        product.setVisibleTags(normalize(request.visibleTags()));
        product.setHiddenTags(normalize(request.hiddenTags()));
        product.setActive(request.active() == null || request.active());

        product = productRepository.save(product);
        activityService.record(principal, "PRODUCT_CREATE", "Product", product.getId(),
                Map.of("name", product.getName()));
        return CompanyProductView.from(product);
    }

    public CompanyProductView update(AppPrincipal principal, String id, UpdateProductRequest request) {
        var product = require(principal, id);

        if (request.name() != null) {
            product.setName(request.name().trim());
        }
        if (request.description() != null) {
            product.setDescription(request.description().trim());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.currency() != null && !request.currency().isBlank()) {
            product.setCurrency(request.currency().trim().toUpperCase());
        }
        if (request.stock() != null) {
            product.setStock(request.stock());
        }
        if (request.images() != null) {
            product.setImages(request.images());
        }
        if (request.categories() != null) {
            product.setCategories(normalize(request.categories()));
        }
        if (request.visibleTags() != null) {
            product.setVisibleTags(normalize(request.visibleTags()));
        }
        if (request.hiddenTags() != null) {
            product.setHiddenTags(normalize(request.hiddenTags()));
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }
        // El slug no cambia aunque cambie el nombre: los enlaces ya compartidos deben seguir vivos.
        product.setUpdatedAt(Instant.now());

        product = productRepository.save(product);
        activityService.record(principal, "PRODUCT_UPDATE", "Product", product.getId(),
                Map.of("name", product.getName()));
        return CompanyProductView.from(product);
    }

    public CompanyProductView updateStock(AppPrincipal principal, String id, int stock) {
        var product = require(principal, id);
        int previous = product.getStock();
        product.setStock(stock);
        product.setUpdatedAt(Instant.now());
        product = productRepository.save(product);

        activityService.record(principal, "STOCK_UPDATE", "Product", product.getId(),
                Map.of("de", String.valueOf(previous), "a", String.valueOf(stock)));
        return CompanyProductView.from(product);
    }

    public void delete(AppPrincipal principal, String id) {
        var product = require(principal, id);
        productRepository.delete(product);
        activityService.record(principal, "PRODUCT_DELETE", "Product", id,
                Map.of("name", product.getName()));
    }

    /** Garantiza que la empresa solo pueda tocar sus propios productos. */
    private Product require(AppPrincipal principal, String id) {
        return productRepository.findByIdAndCompanyId(id, companyOf(principal))
                .orElseThrow(() -> ApiException.notFound("El producto no existe en tu empresa."));
    }

    private String companyOf(AppPrincipal principal) {
        if (principal.companyId() == null) {
            throw ApiException.forbidden("NOT_A_COMPANY_MEMBER",
                    "Esta seccion es exclusiva de las cuentas de empresa.");
        }
        return principal.companyId();
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    /** Slug legible a partir del nombre, con sufijo numerico si ya estuviera ocupado. */
    private String uniqueSlug(String name) {
        String base = slugify(name);
        if (base.isEmpty()) {
            base = "producto";
        }
        String candidate = base;
        int suffix = 2;
        while (productRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
