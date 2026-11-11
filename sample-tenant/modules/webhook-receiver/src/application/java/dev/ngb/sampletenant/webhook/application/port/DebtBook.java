package dev.ngb.sampletenant.webhook.application.port;

import java.time.Instant;

/** The tenant's own debt book, fed by Billing's payment failures. */
public interface DebtBook {

    void recordDebt(String billingCustomerId, long minorUnits, String currency, String reason, Instant occurredAt);

    void clearOpenDebts(String billingCustomerId, Instant clearedAt);
}
