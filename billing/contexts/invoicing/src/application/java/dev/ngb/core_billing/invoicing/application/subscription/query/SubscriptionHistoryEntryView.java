package dev.ngb.core_billing.invoicing.application.subscription.query;

import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionHistoryEntry;
import dev.ngb.core_billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionHistoryEntryView(
        UUID id, SubscriptionHistoryEntry.Type type, Integer periodNumber, Instant periodStart, Instant periodEnd, Money amount, UUID chargeId, String detail,
        Instant occurredAt) {}
