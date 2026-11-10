package dev.ngb.core_billing.money.application.charge.query;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import java.util.Optional;

/** Read-only projection of a charge. */
public interface ChargeReadRepository {

    Optional<ChargeView> findById(ChargeId id);
}
