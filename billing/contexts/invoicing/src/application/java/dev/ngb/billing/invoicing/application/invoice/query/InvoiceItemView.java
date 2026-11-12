package dev.ngb.billing.invoicing.application.invoice.query;

import dev.ngb.billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.UUID;

public record InvoiceItemView(
        UUID id, ItemSource source, UUID chargeId, UUID subscriptionId, Instant periodStart, Instant periodEnd, String description, Money amount, Instant incurredAt) {}
