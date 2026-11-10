package io.corebilling.money.application.charge.port;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.domain.charge.Charge;
import java.util.Optional;

public interface ChargeRepository {

    void add(Charge charge);

    Optional<Charge> findById(ChargeId id);

    Optional<Charge> findByIdempotencyKey(String idempotencyKey);
}
