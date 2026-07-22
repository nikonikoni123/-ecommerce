package com.ecommerce.order;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PromotionWindowRepository extends MongoRepository<PromotionWindow, String> {

    /** Ventanas activas que cubren el instante dado. Si hay varias, gana la de mayor descuento. */
    List<PromotionWindow> findByActiveIsTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
            Instant at, Instant sameAt);
}
