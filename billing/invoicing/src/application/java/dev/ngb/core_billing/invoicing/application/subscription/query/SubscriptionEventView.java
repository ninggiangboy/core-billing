package dev.ngb.core_billing.invoicing.application.subscription.query;

import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionEvent;
import dev.ngb.core_billing.sharedkernel.Money;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionEventView(
        UUID id, SubscriptionEvent.Type type, Integer periodNumber, Instant periodStart, Instant periodEnd, Money amount, UUID chargeId, String detail,
        Instant occurredAt) {}
