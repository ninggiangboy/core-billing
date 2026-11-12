package dev.ngb.billing.money.application.charge.query;

import dev.ngb.billing.money.api.charge.ChargeId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projection of a charge. */
public interface ChargeReadRepository {

    Optional<ChargeView> findById(ChargeId id);

    /** Newest first; {@code before} is the id of the last charge of the previous page. */
    List<ChargeView> list(List<UUID> walletIds, UUID before, int limit);
}
