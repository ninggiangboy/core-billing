package dev.ngb.core_billing.money.application.charge.port;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.domain.charge.Charge;
import java.util.Optional;

public interface ChargeRepository {

    void add(Charge charge);

    Optional<Charge> findById(ChargeId id);

    Optional<Charge> findByIdempotencyKey(String idempotencyKey);
}
