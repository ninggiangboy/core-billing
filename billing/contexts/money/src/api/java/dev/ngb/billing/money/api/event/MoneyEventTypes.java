package dev.ngb.billing.money.api.event;

/**
 * The published names of the money events other contexts react to: a consumer compares the type of an outbox event
 * with one of these constants, never with a literal. The schemas in {@code contracts/events} define the payloads.
 */
public final class MoneyEventTypes {

    /** A charge was collected from a wallet. */
    public static final String CHARGE_SUCCEEDED = "charge.succeeded";

    private MoneyEventTypes() {}
}
