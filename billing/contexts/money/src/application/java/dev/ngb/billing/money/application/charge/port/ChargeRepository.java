package dev.ngb.billing.money.application.charge.port;

import dev.ngb.billing.money.api.charge.ChargeId;
import dev.ngb.billing.money.domain.charge.Charge;
import java.util.Optional;

public interface ChargeRepository {

    void add(Charge charge);

    Optional<Charge> findById(ChargeId id);

    Optional<Charge> findByIdempotencyKey(String idempotencyKey);
}
