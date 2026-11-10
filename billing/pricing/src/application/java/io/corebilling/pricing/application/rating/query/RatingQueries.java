package io.corebilling.pricing.application.rating.query;

import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.sharedkernel.UnitOfWork;
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
