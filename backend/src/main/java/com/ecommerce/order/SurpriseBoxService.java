package com.ecommerce.order;

import com.ecommerce.catalog.Product;
import com.ecommerce.catalog.ProductRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.order.dto.OrderDtos.SurpriseProposal;
import com.ecommerce.order.dto.OrderDtos.SurpriseRequest;
import com.ecommerce.user.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Caja sorpresa: la plataforma elige los productos y, a cambio de renunciar a escoger, el cliente
 * se lleva el descuento de pedido aleatorio.
 *
 * <p>La eleccion no es puramente al azar. Se reutiliza la <b>afinidad por etiquetas invisibles</b>
 * que ya acumula el catalogo en {@code users.tagAffinity}: los productos que encajan con el interes
 * del cliente entran en el sorteo con mas peso. Asi la sorpresa se parece a lo que le gusta en vez
 * de ser un saldo aleatorio, sin que las etiquetas se expongan en ningun momento.
 */
@Service
public class SurpriseBoxService {

    private static final int MAX_INTENTOS_PRESUPUESTO = 40;

    private final ProductRepository products;
    private final UserRepository users;
    private final PricingService pricingService;
    private final DiscountService discountService;
    private final SecureRandom random = new SecureRandom();

    public SurpriseBoxService(ProductRepository products, UserRepository users,
                              PricingService pricingService, DiscountService discountService) {
        this.products = products;
        this.users = users;
        this.pricingService = pricingService;
        this.discountService = discountService;
    }

    public SurpriseProposal propose(String customerId, SurpriseRequest request) {
        int cuantos = request.items() == null ? 3 : request.items();

        var candidatos = products.findByActiveIsTrue().stream()
                .filter(Product::isInStock)
                .filter(p -> request.category() == null || request.category().isBlank()
                        || p.getCategories().contains(request.category().trim().toLowerCase()))
                .toList();

        if (candidatos.isEmpty()) {
            throw ApiException.badRequest("NO_PRODUCTS",
                    "Ahora mismo no hay productos disponibles para armar una caja sorpresa.");
        }

        var afinidad = users.findById(customerId)
                .map(u -> u.getTagAffinity())
                .orElseGet(Map::of);

        var seleccion = elegir(candidatos, Math.min(cuantos, candidatos.size()), afinidad,
                request.maxTotal());

        var lineas = seleccion.stream()
                .map(p -> new com.ecommerce.order.dto.OrderDtos.OrderItemView(
                        p.getId(), p.getName(), p.getSlug(),
                        p.getImages().isEmpty() ? null : p.getImages().get(0),
                        p.getPrice(), 1, PricingService.lineTotal(p.getPrice(), 1)))
                .toList();

        BigDecimal subtotal = lineas.stream()
                .map(com.ecommerce.order.dto.OrderDtos.OrderItemView::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Estimacion informativa: el importe definitivo se fija al pagar.
        var estimado = discountService.calculate(subtotal, customerId, true, Instant.now());
        var quote = pricingService.quote(
                seleccion.stream()
                        .map(p -> new PricingService.Line(p.getId(), p.getPrice(), 1))
                        .toList(),
                customerId, true, Instant.now());

        String mensaje = estimado.isEmpty()
                ? "La promocion no esta activa ahora mismo, asi que la caja no lleva descuento."
                : "Incluye un %s%% de descuento por pedido sorpresa.".formatted(
                        estimado.percent().stripTrailingZeros().toPlainString());

        return new SurpriseProposal(lineas, subtotal, estimado.percent(), quote.total(),
                seleccion.get(0).getCurrency(), mensaje);
    }

    /**
     * Sorteo ponderado por afinidad, sin repetir producto.
     *
     * <p>Si hay presupuesto se reintenta un numero acotado de veces y, si aun asi no cabe, se
     * devuelve la mejor combinacion encontrada: es preferible proponer algo a no proponer nada.
     */
    private List<Product> elegir(List<Product> candidatos, int cuantos, Map<String, Double> afinidad,
                                 BigDecimal presupuesto) {
        List<Product> mejor = List.of();

        for (int intento = 0; intento < MAX_INTENTOS_PRESUPUESTO; intento++) {
            var disponibles = new ArrayList<>(candidatos);
            var elegidos = new ArrayList<Product>();

            while (elegidos.size() < cuantos && !disponibles.isEmpty()) {
                elegidos.add(sacarPonderado(disponibles, afinidad));
            }

            if (presupuesto == null || presupuesto.compareTo(BigDecimal.ZERO) <= 0) {
                return elegidos;
            }

            BigDecimal total = elegidos.stream()
                    .map(Product::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (total.compareTo(presupuesto) <= 0) {
                return elegidos;
            }
            if (mejor.isEmpty() || total.compareTo(sumar(mejor)) < 0) {
                mejor = elegidos;
            }
        }

        // Ninguna combinacion cupo en el presupuesto: se recorta a lo mas barato que si cabe.
        var ordenados = new ArrayList<>(mejor);
        ordenados.sort(Comparator.comparing(Product::getPrice));
        var ajustado = new ArrayList<Product>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (var p : ordenados) {
            if (acumulado.add(p.getPrice()).compareTo(presupuesto) <= 0) {
                ajustado.add(p);
                acumulado = acumulado.add(p.getPrice());
            }
        }
        return ajustado.isEmpty() ? List.of(ordenados.get(0)) : ajustado;
    }

    /** Extrae un producto con probabilidad proporcional a su afinidad, y lo quita de la bolsa. */
    private Product sacarPonderado(List<Product> disponibles, Map<String, Double> afinidad) {
        double total = 0;
        var pesos = new double[disponibles.size()];
        for (int i = 0; i < disponibles.size(); i++) {
            pesos[i] = peso(disponibles.get(i), afinidad);
            total += pesos[i];
        }

        double objetivo = random.nextDouble() * total;
        for (int i = 0; i < pesos.length; i++) {
            objetivo -= pesos[i];
            if (objetivo <= 0) {
                return disponibles.remove(i);
            }
        }
        return disponibles.remove(disponibles.size() - 1);
    }

    /** Peso base 1 para que todo producto tenga opciones, mas la afinidad acumulada. */
    private double peso(Product producto, Map<String, Double> afinidad) {
        double extra = producto.getHiddenTags().stream()
                .mapToDouble(t -> afinidad.getOrDefault(t, 0.0))
                .sum();
        return 1.0 + extra;
    }

    private BigDecimal sumar(List<Product> productos) {
        return productos.stream().map(Product::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
