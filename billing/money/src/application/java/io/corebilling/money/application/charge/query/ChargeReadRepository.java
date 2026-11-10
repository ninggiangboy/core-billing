package io.corebilling.money.application.charge.query;

import io.corebilling.money.api.charge.ChargeId;
import java.util.Optional;

/** Read-only projection of a charge. */
public interface ChargeReadRepository {

    Optional<ChargeView> findById(ChargeId id);
}
