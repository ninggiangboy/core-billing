package dev.ngb.billing.pricing.application.rating.query;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.customer.api.lookup.CustomerLookup;
import dev.ngb.billing.platform.api.error.NotFoundException;
import java.util.UUID;

/** Reads stored ratings of the current tenant. */
public class RatingQueries {

    private final UnitOfWork unitOfWork;
    private final RatingReadRepository ratings;
    private final CustomerLookup customers;

    public RatingQueries(UnitOfWork unitOfWork, RatingReadRepository ratings, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.ratings = ratings;
        this.customers = customers;
    }

    public UsageRatingView get(UUID id) {
        return get(id, null);
    }

    public UsageRatingView get(UUID id, UUID caller) {
        return unitOfWork.transactional(() -> {
            UsageRatingView rating = ratings.findById(id).orElseThrow(() -> new NotFoundException("usage rating not found"));
            if (caller != null && customers.findFor(rating.customerId(), caller).isEmpty()) {
                throw new NotFoundException("usage rating not found");
            }
            return rating;
        });
    }
}
