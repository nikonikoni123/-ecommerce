package com.ecommerce.order;

import com.ecommerce.catalog.Product;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Reserva y devolucion de stock.
 *
 * <p>La resta se hace con una <b>unica operacion atomica de Mongo</b>: el filtro exige que queden
 * al menos las unidades pedidas y el {@code $dec} se aplica en el mismo paso. Leer el stock, restar
 * en memoria y guardar seria una condicion de carrera de manual: dos compras simultaneas del ultimo
 * articulo leerian el mismo valor y ambas creerian haberlo conseguido.
 */
@Service
public class StockService {

    private final MongoTemplate mongo;

    public StockService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Descuenta {@code quantity} unidades solo si hay suficientes.
     *
     * @return {@code true} si la reserva se hizo; {@code false} si otro comprador se adelanto
     */
    public boolean reserve(String productId, int quantity) {
        var result = mongo.updateFirst(
                new Query(Criteria.where("_id").is(productId).and("stock").gte(quantity)),
                new Update().inc("stock", -quantity),
                Product.class);
        return result.getModifiedCount() == 1;
    }

    /** Devuelve unidades al catalogo: compensacion cuando el pago falla a mitad. */
    public void release(String productId, int quantity) {
        mongo.updateFirst(
                new Query(Criteria.where("_id").is(productId)),
                new Update().inc("stock", quantity),
                Product.class);
    }
}
