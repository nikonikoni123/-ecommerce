package com.ecommerce.config;

import com.ecommerce.activity.ActivityLog;
import com.ecommerce.auth.token.RefreshToken;
import com.ecommerce.auth.token.VerificationToken;
import com.ecommerce.catalog.Product;
import com.ecommerce.company.Company;
import com.ecommerce.company.Role;
import com.ecommerce.notification.Notification;
import com.ecommerce.user.User;
import java.time.Duration;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

/**
 * Indices declarados explicitamente en lugar de con {@code @Indexed}, para tenerlos todos a la
 * vista y controlar cuando se crean ({@code auto-index-creation} esta desactivado).
 */
@Component
public class MongoIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexConfig.class);

    private final MongoTemplate mongo;

    public MongoIndexConfig(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        // --- users ---
        var users = mongo.indexOps(User.class);
        users.createIndex(new Index().on("email", Sort.Direction.ASC).unique().named("uk_users_email"));
        // El username solo existe en los clientes: indice parcial para no chocar con los nulos
        // de los miembros de empresa, que comparten coleccion.
        users.createIndex(new Index()
                .on("username", Sort.Direction.ASC)
                .unique()
                .named("uk_users_username")
                .partial(PartialIndexFilter.of(
                        new Document("username", new Document("$type", "string")))));
        users.createIndex(new Index().on("companyId", Sort.Direction.ASC).named("ix_users_company"));
        // Garantiza un unico usuario root por empresa.
        users.createIndex(new Index()
                .on("companyId", Sort.Direction.ASC)
                .unique()
                .named("uk_users_company_root")
                .partial(PartialIndexFilter.of(new Document("root", true))));

        // --- companies ---
        var companies = mongo.indexOps(Company.class);
        companies.createIndex(new Index().on("nit", Sort.Direction.ASC).unique().named("uk_companies_nit"));

        // --- roles ---
        mongo.indexOps(Role.class)
                .createIndex(new Index().on("companyId", Sort.Direction.ASC).named("ix_roles_company"));

        // --- products ---
        var products = mongo.indexOps(Product.class);
        products.createIndex(new Index().on("slug", Sort.Direction.ASC).unique().named("uk_products_slug"));
        products.createIndex(new Index().on("companyId", Sort.Direction.ASC).named("ix_products_company"));
        products.createIndex(new Index()
                .on("active", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("ix_products_active_recent"));
        // Busqueda por texto en el catalogo publico.
        mongo.getCollection(mongo.getCollectionName(Product.class))
                .createIndex(new Document("name", "text").append("description", "text"),
                        new com.mongodb.client.model.IndexOptions()
                                .name("tx_products_search")
                                .defaultLanguage("spanish"));

        // --- tokens: se autoeliminan al vencer ---
        mongo.indexOps(VerificationToken.class).createIndex(new Index()
                .on("expiresAt", Sort.Direction.ASC)
                .expire(Duration.ZERO)
                .named("ttl_verification_tokens"));
        mongo.indexOps(VerificationToken.class).createIndex(
                new Index().on("token", Sort.Direction.ASC).unique().named("uk_verification_token"));
        mongo.indexOps(RefreshToken.class).createIndex(new Index()
                .on("expiresAt", Sort.Direction.ASC)
                .expire(Duration.ZERO)
                .named("ttl_refresh_tokens"));
        mongo.indexOps(RefreshToken.class).createIndex(
                new Index().on("tokenHash", Sort.Direction.ASC).unique().named("uk_refresh_token_hash"));

        // --- carrito: uno por cliente ---
        mongo.indexOps(com.ecommerce.cart.Cart.class).createIndex(
                new Index().on("customerId", Sort.Direction.ASC).unique().named("uk_carts_customer"));

        // --- pedidos ---
        var orders = mongo.indexOps(com.ecommerce.order.Order.class);
        orders.createIndex(new Index().on("number", Sort.Direction.ASC).unique().named("uk_orders_number"));
        orders.createIndex(new Index()
                .on("customerId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("ix_orders_customer"));
        // El panel de la empresa (Etapa 3) ordena por estado y fecha.
        orders.createIndex(new Index()
                .on("companyId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("ix_orders_company_status"));

        // --- ventanas de promocion ---
        mongo.indexOps(com.ecommerce.order.PromotionWindow.class).createIndex(new Index()
                .on("active", Sort.Direction.ASC)
                .on("startsAt", Sort.Direction.ASC)
                .on("endsAt", Sort.Direction.ASC)
                .named("ix_promotions_window"));

        // --- notifications y actividad ---
        mongo.indexOps(Notification.class).createIndex(new Index()
                .on("recipientId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("ix_notifications_recipient"));
        mongo.indexOps(ActivityLog.class).createIndex(new Index()
                .on("companyId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("ix_activity_company"));

        log.info("Indices de MongoDB verificados");
    }
}
