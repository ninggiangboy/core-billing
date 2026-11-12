package dev.ngb.billing.pricing.application.rating.query;

import java.util.Optional;
import java.util.UUID;

/** Read-only view of a stored rating. */
public interface RatingReadRepository {

    Optional<UsageRatingView> findById(UUID id);
}
