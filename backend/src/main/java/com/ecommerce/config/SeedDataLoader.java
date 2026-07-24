package com.ecommerce.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ecommerce.catalog.Product;
import com.ecommerce.catalog.ProductRepository;
import com.ecommerce.company.Company;
import com.ecommerce.company.CompanyRepository;
import com.ecommerce.company.Role;
import com.ecommerce.company.RoleRepository;
import com.ecommerce.order.PromotionWindow;
import com.ecommerce.order.PromotionWindowRepository;
import com.ecommerce.security.Permission;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserType;

/**
 * Datos de demostracion para poder probar la aplicacion nada mas levantarla.
 *
 * <p>Solo se ejecuta si la base esta vacia y {@code app.seed-demo-data} sigue activo, de modo que
 * nunca pisa informacion real.
 */
@Component
@Order(100)   // despues de MongoIndexConfig, para que los indices unicos ya existan
public class SeedDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private static final String DEMO_PASSWORD = "Demo1234!";

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final ProductRepository productRepository;
    private final PromotionWindowRepository promotionWindowRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataLoader(AppProperties properties, UserRepository userRepository,
                          CompanyRepository companyRepository, RoleRepository roleRepository,
                          ProductRepository productRepository,
                          PromotionWindowRepository promotionWindowRepository,
                          PasswordEncoder passwordEncoder) {
        this.promotionWindowRepository = promotionWindowRepository;
        this.properties = properties;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {

        if (!properties.seedDemoData()) {
            return;
        }

        // La ventana de promocion se siembra por separado: sin ella no hay ningun descuento, y las
        // bases creadas antes de la Etapa 2 ya tienen empresas, asi que no entrarian por el bloque
        // de datos de demostracion.
        seedPromotionWindow();

        if (companyRepository.count() > 0) {
            return;
        }
        log.info("Cargando datos de demostracion");

        var company = new Company();
        company.setName("Laboratorio Botanico");
        company.setLegalRepresentative("Ana Restrepo");
        company.setNit("900123456-7");
        company.setAddress("Calle 93 #11-27, Bogota");
        company.setPostalCode("110221");
        company.setEmail("empresa@demo.local");
        company.setPhone("+57 601 555 0123");
        company.setDescription("Formulas de cuidado facial desarrolladas con activos de origen vegetal "
                + "y respaldo dermatologico.");
        company.setCategoryTags(List.of("cuidado facial", "limpieza", "tratamiento"));
        company.setVerified(true);
        company = companyRepository.save(company);

        var root = new User();
        root.setType(UserType.COMPANY_MEMBER);
        root.setEmail("empresa@demo.local");
        root.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        root.setFirstName("Ana Restrepo");
        root.setPhone(company.getPhone());
        root.setAddress(company.getAddress());
        root.setPostalCode(company.getPostalCode());
        root.setCompanyId(company.getId());
        root.setRoot(true);
        root.setPosition("Representante legal");
        root.setEmailVerified(true);
        root = userRepository.save(root);

        company.setRootUserId(root.getId());
        companyRepository.save(company);

        var productManager = new Role();
        productManager.setCompanyId(company.getId());
        productManager.setName("Gestor de productos");
        productManager.setDescription("Gestiona el catalogo y el inventario");
        productManager.setPermissions(EnumSet.of(Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE,
                Permission.PRODUCT_UPDATE, Permission.STOCK_UPDATE));
        productManager.setSystem(true);
        productManager = roleRepository.save(productManager);

        // Miembro sin PRODUCT_DELETE: sirve para comprobar que el control de permisos funciona.
        var member = new User();
        member.setType(UserType.COMPANY_MEMBER);
        member.setEmail("gestor@demo.local");
        member.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        member.setFirstName("Carlos Munoz");
        member.setCompanyId(company.getId());
        member.setPhone(company.getPhone());
        member.setAddress(company.getAddress());
        member.setPostalCode(company.getPostalCode());
        member.setPosition("Gestor de catalogo");
        member.setManagerId(root.getId());
        member.setRoleIds(Set.of(productManager.getId()));
        member.setEmailVerified(true);
        userRepository.save(member);

        var customer = new User();
        customer.setType(UserType.CUSTOMER);
        customer.setEmail("cliente@demo.local");
        customer.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        customer.setFirstName("Laura");
        customer.setLastName("Gomez");
        customer.setUsername("laura");
        customer.setAddress("Carrera 7 #45-12, Bogota");
        customer.setPostalCode("110311");
        customer.setPhone("+57 300 555 0199");
        customer.setEmailVerified(true);
        userRepository.save(customer);

        productRepository.saveAll(demoProducts(company));

        log.info("Datos de demostracion listos. Cuentas: empresa@demo.local / gestor@demo.local / "
                + "cliente@demo.local, contrasena {}", DEMO_PASSWORD);
    }
    
    private void seedPromotionWindow() {
        if (promotionWindowRepository.count() > 0) {
            return;
        }
        var window = new PromotionWindow();
        window.setName("Temporada de lanzamiento");
        window.setStartsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        window.setEndsAt(Instant.now().plus(365, ChronoUnit.DAYS));
        window.setOrderDiscountPercent(new BigDecimal("10"));
        window.setRandomOrderDiscountPercent(new BigDecimal("50"));
        window.setActive(true);
        promotionWindowRepository.save(window);

        log.info("Ventana de promocion activa: 10% en toda orden, 50% adicional en pedido sorpresa");
    }

    private List<Product> demoProducts(Company company) {
        return List.of(
                product(company, "Gel Limpiador Enzimatico",
                        "Limpieza diaria que disuelve el exceso de sebo sin alterar la barrera cutanea. "
                                + "Textura en gel de aclarado rapido.",
                        new BigDecimal("89000"), 120,
                        List.of("limpieza"), List.of("piel mixta", "uso diario"),
                        List.of("rutina-manana", "sensible", "primera-compra")),
                product(company, "Serum Niacinamida 10%",
                        "Concentrado que unifica el tono y reduce la apariencia de los poros. "
                                + "Se aplica sobre la piel limpia, antes de la hidratacion.",
                        new BigDecimal("142000"), 64,
                        List.of("tratamiento"), List.of("manchas", "poros"),
                        List.of("activo-alto", "rutina-noche", "recompra")),
                product(company, "Crema Barrera Ceramidas",
                        "Hidratacion prolongada con ceramidas y escualano para reforzar la barrera. "
                                + "Acabado mate, apta bajo maquillaje.",
                        new BigDecimal("128000"), 88,
                        List.of("hidratacion"), List.of("piel seca", "barrera"),
                        List.of("rutina-noche", "sensible", "recompra")),
                product(company, "Protector Solar Fluido SPF50",
                        "Filtro de amplio espectro con acabado invisible, sin residuo blanco. "
                                + "Reaplicar cada dos horas de exposicion.",
                        new BigDecimal("115000"), 200,
                        List.of("proteccion"), List.of("spf50", "uso diario"),
                        List.of("rutina-manana", "primera-compra")),
                product(company, "Exfoliante Acido Mandelico",
                        "Renovacion suave semanal que mejora la textura sin irritar. "
                                + "Uso nocturno, dos veces por semana.",
                        new BigDecimal("96000"), 45,
                        List.of("tratamiento"), List.of("textura", "semanal"),
                        List.of("activo-alto", "rutina-noche")),
                product(company, "Agua Micelar Calmante",
                        "Desmaquillante sin aclarado con pantenol y avena coloidal. "
                                + "Indicada para piel reactiva.",
                        new BigDecimal("72000"), 150,
                        List.of("limpieza"), List.of("desmaquillante", "piel sensible"),
                        List.of("sensible", "primera-compra")),
                product(company, "Contorno de Ojos Peptidos",
                        "Tratamiento ligero para la zona periocular que atenua la apariencia de "
                                + "lineas finas y suaviza el aspecto de las ojeras.",
                        new BigDecimal("134000"), 0,
                        List.of("tratamiento"), List.of("contorno", "peptidos"),
                        List.of("activo-alto", "recompra")),
                product(company, "Mascarilla Arcilla Verde",
                        "Mascarilla purificante de uso semanal que absorbe el exceso de grasa "
                                + "en la zona T.",
                        new BigDecimal("68000"), 76,
                        List.of("tratamiento"), List.of("purificante", "zona t"),
                        List.of("rutina-noche", "primera-compra")));
    }

    private Product product(Company company, String name, String description, BigDecimal price,
                            int stock, List<String> categories, List<String> visibleTags,
                            List<String> hiddenTags) {
        var product = new Product();
        product.setCompanyId(company.getId());
        product.setCompanyName(company.getName());
        product.setName(name);
        product.setSlug(slugify(name));
        product.setDescription(description);
        product.setPrice(price);
        product.setStock(stock);
        product.setCategories(categories);
        product.setVisibleTags(visibleTags);
        product.setHiddenTags(hiddenTags);
        return product;
    }

    private String slugify(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
