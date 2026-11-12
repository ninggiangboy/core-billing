package dev.ngb.billing.invoicing.domain.invoice;

import dev.ngb.billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of an invoice. Every line is backed by a succeeded charge, which is where the amount was taken from
 * the wallet, so an invoice never asks for money the ledger does not already record as owed. A subscription line
 * also names the subscription and the period it covers.
 */
public record InvoiceItem(
        UUID id, ItemSource source, UUID chargeId, UUID subscriptionId, Instant periodStart, Instant periodEnd, String description, Money amount,
        Instant incurredAt) {

    public static final int MAX_DESCRIPTION = 500;

    public InvoiceItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(chargeId, "chargeId");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(incurredAt, "incurredAt");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("an invoice line must be positive");
        }
        if (description.isBlank() || description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("a line description is 1 to " + MAX_DESCRIPTION + " characters");
        }
        if ((source == ItemSource.SUBSCRIPTION_PERIOD) != (subscriptionId != null)) {
            throw new IllegalArgumentException("exactly the subscription lines name a subscription");
        }
        if ((periodStart == null) != (periodEnd == null) || periodStart != null && !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("a line period needs both ends, in order");
        }
    }
}
