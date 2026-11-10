package dev.ngb.core_billing.pricing.application.rating.query;

import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.UUID;

/** Reads stored ratings of the current tenant. */
public class RatingQueries {

    private final UnitOfWork unitOfWork;
    private final RatingReadRepository ratings;

    public RatingQueries(UnitOfWork unitOfWork, RatingReadRepository ratings) {
        this.unitOfWork = unitOfWork;
        this.ratings = ratings;
    }

    public UsageRatingView get(UUID id) {
        return unitOfWork.transactional(() -> ratings.findById(id).orElseThrow(() -> new NotFoundException("usage rating not found")));
    }
}
