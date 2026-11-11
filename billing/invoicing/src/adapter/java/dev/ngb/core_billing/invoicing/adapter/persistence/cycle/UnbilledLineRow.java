package dev.ngb.core_billing.invoicing.adapter.persistence.cycle;

import dev.ngb.core_billing.invoicing.domain.cycle.UnbilledLine;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.core_billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("invoice_items")
record UnbilledLineRow(
        @Id UUID id,
        UUID tenantId,
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
        Instant cycleStart,
        Instant cycleEnd,
        Instant incurredAt) {

    UnbilledLine toDomain() {
        InvoiceItem item = new InvoiceItem(id, ItemSource.valueOf(source), chargeId, subscriptionId, periodStart, periodEnd, description,
                Money.of(amount, Currency.of(currency.strip())), incurredAt);
        return new UnbilledLine(new TenantId(tenantId), new CustomerId(customerId), new WalletId(walletId), item, cycleStart, cycleEnd);
    }
}
