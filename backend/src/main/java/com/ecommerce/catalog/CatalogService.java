package com.ecommerce.catalog;

import com.ecommerce.catalog.dto.ProductDtos.CatalogQuery;
import com.ecommerce.catalog.dto.ProductDtos.ProductDetail;
import com.ecommerce.catalog.dto.ProductDtos.ProductSummary;
import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

/**
 * Catalogo publico.
 *
 * <p>Los productos se priorizan de forma personalizada mediante etiquetas invisibles: cada producto
 * lleva {@code hiddenTags} y cada cliente acumula una afinidad por etiqueta segun los productos que
 * consulta. El listado ordena por esa afinidad antes que por novedad, de modo que el comprador ve
 * primero lo que encaja con su interes sin que las etiquetas se expongan nunca en la respuesta.
 */
@Service
public class CatalogService {

    /** Cuanto sube la afinidad al abrir la ficha de un producto. */
    private static final double VIEW_AFFINITY_INCREMENT = 1.0;
    /** Techo por etiqueta, para que un interes puntual no domine el catalogo para siempre. */
    private static final double MAX_AFFINITY = 25.0;

    private final MongoTemplate mongo;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CatalogService(MongoTemplate mongo, ProductRepository productRepository,
                          UserRepository userRepository) {
        this.mongo = mongo;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<ProductSummary> browse(CatalogQuery filters, int page, int size,
                                               AppPrincipal principal) {
        Query query = buildQuery(filters);
        long total = mongo.count(query, Product.class);

        Map<String, Double> affinity = affinityOf(principal);

        boolean explicitSort = filters.sort() != null && !filters.sort().isBlank();
        if (affinity.isEmpty() || explicitSort) {
            // Sin afinidad, o con un orden pedido explicitamente, se pagina en la base de datos.
            query.with(sortFor(filters.sort()));
            query.skip((long) page * size).limit(size);
            var content = mongo.find(query, Product.class).stream().map(ProductSummary::from).toList();
            return PageResponse.of(content, page, size, total);
        }

        // Con afinidad se ordena por la puntuacion personalizada. Se acota el numero de candidatos
        // para que el coste no crezca con el catalogo entero.
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(500);
        List<Product> candidates = new ArrayList<>(mongo.find(query, Product.class));
        candidates.sort(Comparator
                .comparingDouble((Product p) -> -score(p, affinity))
                .thenComparing(Product::getCreatedAt, Comparator.reverseOrder()));

        int from = Math.min(page * size, candidates.size());
        int to = Math.min(from + size, candidates.size());
        var content = candidates.subList(from, to).stream().map(ProductSummary::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    private Query buildQuery(CatalogQuery filters) {
        var criteria = new ArrayList<Criteria>();
        criteria.add(Criteria.where("active").is(true));

        if (filters.category() != null && !filters.category().isBlank()) {
            criteria.add(Criteria.where("categories").is(filters.category().trim().toLowerCase()));
        }
        if (filters.minPrice() != null) {
            criteria.add(Criteria.where("price").gte(filters.minPrice()));
        }
        if (filters.maxPrice() != null) {
            criteria.add(Criteria.where("price").lte(filters.maxPrice()));
        }
        if (Boolean.TRUE.equals(filters.inStockOnly())) {
            criteria.add(Criteria.where("stock").gt(0));
        }

        Query query = new Query(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));
        if (filters.search() != null && !filters.search().isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(filters.search().trim()));
        }
        return query;
    }

    private Sort sortFor(String sort) {
        return switch (sort == null ? "" : sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private double score(Product product, Map<String, Double> affinity) {
        double total = 0;
        for (String tag : product.getHiddenTags()) {
            total += affinity.getOrDefault(tag, 0.0);
        }
        return total;
    }

    private Map<String, Double> affinityOf(AppPrincipal principal) {
        if (principal == null || principal.type() != UserType.CUSTOMER) {
            return Map.of();
        }
        return userRepository.findById(principal.userId())
                .map(User::getTagAffinity)
                .orElseGet(Map::of);
    }

    public ProductDetail detail(String slug, AppPrincipal principal) {
        var product = productRepository.findBySlug(slug)
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("El producto no existe o ya no esta disponible."));

        reinforceAffinity(principal, product);
        return ProductDetail.from(product);
    }

    /** Consultar la ficha de un producto refuerza la afinidad del cliente con sus etiquetas. */
    private void reinforceAffinity(AppPrincipal principal, Product product) {
        if (principal == null || principal.type() != UserType.CUSTOMER
                || product.getHiddenTags().isEmpty()) {
            return;
        }
        userRepository.findById(principal.userId()).ifPresent(user -> {
            var affinity = user.getTagAffinity();
            for (String tag : product.getHiddenTags()) {
                affinity.merge(tag, VIEW_AFFINITY_INCREMENT,
                        (current, delta) -> Math.min(current + delta, MAX_AFFINITY));
            }
            user.setTagAffinity(affinity);
            userRepository.save(user);
        });
    }

    /** Categorias con al menos un producto activo, para pintar los filtros del catalogo. */
    public List<String> categories() {
        return mongo.findDistinct(new Query(Criteria.where("active").is(true)), "categories",
                        Product.class, String.class)
                .stream().sorted().toList();
    }
}
