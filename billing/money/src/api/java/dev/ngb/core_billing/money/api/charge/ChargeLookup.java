package dev.ngb.core_billing.money.api.charge;

import java.util.Optional;

/**
 * Read-only lookup for the contexts that build on a charge, such as an invoice for it. It answers within the
 * current tenant scope, so another tenant's charge is simply absent.
 */
public interface ChargeLookup {

    Optional<ChargeSummary> find(ChargeId id);
}
