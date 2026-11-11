package dev.ngb.core_billing.pricing.application.rating.port;

import dev.ngb.core_billing.pricing.domain.rating.UsageRatingId;
import dev.ngb.core_billing.pricing.domain.rating.UsageRating;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Ratings of the current tenant and the link that makes a usage event rateable only once. */
public interface RatingRepository {

    /** @return false when a rating for the same usage set and attempt already exists */
    boolean addIfAbsent(UsageRating rating);

    /** Marks usage events as accounted for by {@code ratingId}. @return how many were newly linked (events already linked are skipped) */
    int link(UsageRatingId ratingId, Collection<UUID> usageEventIds);

    /** Which of these usage events are already accounted for, and by which rating. */
    Map<UUID, UsageRatingId> ratedAmong(Collection<UUID> usageEventIds);

    /** How many earlier attempts at this usage set ended in a failed charge. */
    int failedAttempts(String setKey);

    Optional<UsageRating> findById(UsageRatingId id);
}
