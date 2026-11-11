package dev.ngb.core_billing.invoicing.application.invoice.service;

import dev.ngb.core_billing.invoicing.application.subscription.service.SubscriptionCharges;
import dev.ngb.core_billing.invoicing.application.subscription.port.SubscriptionRepository;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.core_billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.Money;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a succeeded charge into an invoice line, the same way whether it is collected for a cycle or invoiced on
 * its own. A renewal charge becomes a subscription-period line naming the subscription and the period; any other
 * charge becomes a plain line described by where it came from.
 */
public class ChargeLineFactory {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final SubscriptionRepository subscriptions;
    private final IdGenerator ids;

    public ChargeLineFactory(SubscriptionRepository subscriptions, IdGenerator ids) {
        this.subscriptions = subscriptions;
        this.ids = ids;
    }

    /** @param source USAGE, RESERVATION or STANDALONE, as the money context names it */
    public InvoiceItem lineFor(UUID chargeId, String source, String reference, Money amount, Instant incurredAt) {
        Optional<SubscriptionCharges.Period> period = SubscriptionCharges.parse(reference);
        Optional<Subscription> subscription = period.flatMap(p -> subscriptions.findById(p.subscriptionId()));
        if (subscription.isPresent()) {
            Subscription s = subscription.get();
            int number = period.orElseThrow().number();
            Instant start = s.periodStart(number);
            Instant end = s.periodEnd(number);
            return new InvoiceItem(ids.newId(), ItemSource.SUBSCRIPTION_PERIOD, chargeId, s.id().value(), start, end,
                    limit("Subscription " + s.planCode() + ", " + DAY.format(start) + " to " + DAY.format(end)), amount, incurredAt);
        }
        return new InvoiceItem(ids.newId(), ItemSource.CHARGE, chargeId, null, null, null, limit(describe(source, reference)), amount, incurredAt);
    }

    private static String describe(String source, String reference) {
        return switch (source) {
            case "USAGE" -> "Usage" + (reference == null ? "" : " (" + reference + ")");
            case "RESERVATION" -> "Charge from a reservation" + (reference == null ? "" : " (" + reference + ")");
            default -> reference == null || reference.isBlank() ? "Charge" : reference;
        };
    }

    private static String limit(String text) {
        return text.length() > InvoiceItem.MAX_DESCRIPTION ? text.substring(0, InvoiceItem.MAX_DESCRIPTION) : text;
    }
}
