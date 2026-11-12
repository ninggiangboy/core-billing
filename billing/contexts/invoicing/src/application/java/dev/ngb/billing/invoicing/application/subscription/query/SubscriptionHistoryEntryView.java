package dev.ngb.billing.invoicing.application.subscription.query;

import dev.ngb.billing.invoicing.domain.subscription.SubscriptionHistoryEntry;
import dev.ngb.billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionHistoryEntryView(
        UUID id, SubscriptionHistoryEntry.Type type, Integer periodNumber, Instant periodStart, Instant periodEnd, Money amount, UUID chargeId, String detail,
        Instant occurredAt) {}
