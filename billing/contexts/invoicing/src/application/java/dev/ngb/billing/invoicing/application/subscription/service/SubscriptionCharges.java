package dev.ngb.billing.invoicing.application.subscription.service;

import dev.ngb.billing.invoicing.domain.subscription.SubscriptionId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a renewal charge is named. The money context stores the reference text on the charge, and invoicing reads it
 * back to tell a subscription period from any other charge, so both directions live in this one class.
 */
public final class SubscriptionCharges {

    private static final Pattern REFERENCE = Pattern.compile("subscription:([0-9a-f-]{36}):(\\d{1,9})");

    /** The subscription and period a renewal charge pays for. */
    public record Period(SubscriptionId subscriptionId, int number) {}

    private SubscriptionCharges() {}

    /** The reference stored on the charge that pays period {@code number} of a subscription. */
    public static String reference(SubscriptionId subscriptionId, int number) {
        return "subscription:" + subscriptionId + ":" + number;
    }

    /**
     * The charge idempotency key. It names the attempt as well as the period, because a charge that failed for lack
     * of funds is remembered by its key: the next attempt, after a top-up, needs a new one.
     */
    public static String idempotencyKey(SubscriptionId subscriptionId, int number, int attempt) {
        return "renewal:" + subscriptionId + ":" + number + ":" + attempt;
    }

    public static Optional<Period> parse(String reference) {
        if (reference == null) {
            return Optional.empty();
        }
        Matcher matcher = REFERENCE.matcher(reference);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Period(new SubscriptionId(UUID.fromString(matcher.group(1))), Integer.parseInt(matcher.group(2))));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
