package io.corebilling.money.application;

import io.corebilling.money.api.ChargeId;
import io.corebilling.money.domain.Charge;
import java.util.Optional;

public interface ChargeRepository {

    void add(Charge charge);

    Optional<Charge> findById(ChargeId id);

    Optional<Charge> findByIdempotencyKey(String idempotencyKey);
}
