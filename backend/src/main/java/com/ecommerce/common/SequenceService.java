package com.ecommerce.common;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Contadores atomicos para numerar pedidos y facturas.
 *
 * <p>Se usa {@code findAndModify} con {@code $inc}, que Mongo resuelve en una sola operacion
 * atomica: dos compras simultaneas nunca reciben el mismo numero. Contar documentos para deducir
 * el siguiente numero si daria duplicados bajo concurrencia.
 */
@Service
public class SequenceService {

    private static final String COLLECTION = "counters";

    private final MongoTemplate mongo;

    public SequenceService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Numero de pedido legible: {@code ORD-2026-000123}, reiniciado cada año. */
    public String nextOrderNumber() {
        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        long value = next("orders-" + year);
        return "ORD-%d-%06d".formatted(year, value);
    }

    /** Numero de factura, con su propia serie independiente de la de pedidos. */
    public String nextInvoiceNumber() {
        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        long value = next("invoices-" + year);
        return "FAC-%d-%06d".formatted(year, value);
    }

    public long next(String key) {
        var counter = mongo.findAndModify(
                new Query(Criteria.where("_id").is(key)),
                new Update().inc("value", 1),
                // upsert: crea el contador la primera vez; returnNew: devuelve el valor ya sumado.
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Counter.class,
                COLLECTION);
        return counter == null ? 1L : counter.value();
    }

    /** Documento del contador. Solo lo usa este servicio. */
    public record Counter(String id, long value) {
    }
}
