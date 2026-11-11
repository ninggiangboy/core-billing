package dev.ngb.core_billing.invoicing.application.invoice.query;

import dev.ngb.core_billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.core_billing.sharedkernel.Money;
import java.time.Instant;
import java.util.UUID;

public record InvoiceItemView(
        UUID id, ItemSource source, UUID chargeId, UUID subscriptionId, Instant periodStart, Instant periodEnd, String description, Money amount, Instant incurredAt) {}
