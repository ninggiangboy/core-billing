package dev.ngb.billing.invoicing.domain.cycle;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing.sharedkernel.money.Currency;
import java.time.Instant;
import java.util.Objects;

/**
 * A Postpaid charge waiting for its aggregation period to close. The period is a calendar bucket (UTC) fixed
 * when the charge is collected; the invoice for it is created by the cycle job once the bucket has ended.
 */
public record UnbilledLine(TenantId tenantId, CustomerId customerId, WalletId walletId, InvoiceItem item, Instant cycleStart, Instant cycleEnd) {

    public UnbilledLine {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(cycleStart, "cycleStart");
        if (!cycleEnd.isAfter(cycleStart)) {
            throw new IllegalArgumentException("a billing period ends after it starts");
        }
    }

    public Currency currency() {
        return item.amount().currency();
    }
}
