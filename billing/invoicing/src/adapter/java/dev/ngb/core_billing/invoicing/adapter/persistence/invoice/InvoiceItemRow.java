package dev.ngb.core_billing.invoicing.adapter.persistence.invoice;

import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.core_billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A line as stored. It is written by an explicit upsert (a line may exist unbilled before its invoice does), so the
 * row only carries the shape; the aggregation columns are set by the unbilled-line repository.
 */
@Table("invoice_items")
record InvoiceItemRow(
        @Id UUID id,
        UUID tenantId,
        UUID invoiceId,
        UUID customerId,
        UUID walletId,
        String currency,
        String source,
        UUID chargeId,
        UUID subscriptionId,
        String description,
        long amount,
        Instant periodStart,
        Instant periodEnd,
        Instant incurredAt) {

    InvoiceItem toDomain() {
        return new InvoiceItem(id, ItemSource.valueOf(source), chargeId, subscriptionId, periodStart, periodEnd, description,
                Money.of(amount, Currency.of(currency.strip())), incurredAt);
    }
}
