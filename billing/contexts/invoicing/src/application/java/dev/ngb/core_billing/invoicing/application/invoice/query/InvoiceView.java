package dev.ngb.core_billing.invoicing.application.invoice.query;

import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceOrigin;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceStatus;
import dev.ngb.core_billing.invoicing.domain.invoice.Settlement;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Instant;
import java.util.UUID;

/** An invoice as the read API shows it, without its lines. */
public record InvoiceView(
        UUID id,
        String number,
        CustomerId customerId,
        WalletId walletId,
        InvoiceStatus status,
        InvoiceOrigin origin,
        Money total,
        Instant periodStart,
        Instant periodEnd,
        Settlement settlement,
        String voidReason,
        Instant createdAt,
        Instant issuedAt,
        Instant paidAt,
        Instant voidedAt) {}
