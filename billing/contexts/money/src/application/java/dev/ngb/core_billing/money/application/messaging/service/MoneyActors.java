package dev.ngb.core_billing.money.application.messaging.service;

/**
 * The names money acts under when no user is behind the change. A name is stored in the ledger entries and the audit
 * log, so it must not change once released.
 */
public final class MoneyActors {

    /** The actor that records a charge on a caller's behalf. */
    public static final String CHARGE = "system/charge";

    /** The actor that audits a ledger mismatch. */
    public static final String LEDGER_VERIFY = "system/ledger-verify";

    private MoneyActors() {}
}
