package io.corebilling.pricing.application;

import io.corebilling.platform.api.NotFoundException;
import io.corebilling.pricing.domain.RatingId;
import io.corebilling.pricing.domain.UsageRating;
import io.corebilling.sharedkernel.UnitOfWork;

/** Reads ratings of the current tenant. */
public class RatingQueries {

    private final UnitOfWork unitOfWork;
    private final RatingRepository ratings;

    public RatingQueries(UnitOfWork unitOfWork, RatingRepository ratings) {
        this.unitOfWork = unitOfWork;
        this.ratings = ratings;
    }

    public UsageRating get(RatingId id) {
        return unitOfWork.transactional(() -> ratings.findById(id).orElseThrow(() -> new NotFoundException("usage rating not found")));
    }
}
