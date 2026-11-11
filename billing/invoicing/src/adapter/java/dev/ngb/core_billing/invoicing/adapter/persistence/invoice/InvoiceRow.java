package dev.ngb.core_billing.invoicing.adapter.persistence.invoice;

import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceOrigin;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoicePayment;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceStatus;
import dev.ngb.core_billing.invoicing.domain.invoice.Settlement;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("invoices")
record InvoiceRow(
        @Id UUID id,
        UUID tenantId,
        UUID customerId,
        UUID walletId,
        String currency,
        String number,
        String origin,
        String status,
        long total,
        Instant periodStart,
        Instant periodEnd,
        String settlement,
        String voidReason,
        Instant createdAt,
        Instant issuedAt,
        Instant paidAt,
        Instant voidedAt) {

    static InvoiceRow from(Invoice invoice) {
        return new InvoiceRow(
                invoice.id().value(),
                invoice.tenantId().value(),
                invoice.customerId().value(),
                invoice.walletId().value(),
                invoice.currency().code(),
                invoice.number(),
                invoice.origin().name(),
                invoice.status().name(),
                invoice.total().minorUnits(),
                invoice.periodStart(),
                invoice.periodEnd(),
                invoice.settlement() == null ? null : invoice.settlement().name(),
                invoice.voidReason(),
                invoice.createdAt(),
                invoice.issuedAt(),
                invoice.paidAt(),
                invoice.voidedAt());
    }

    Invoice toDomain(List<InvoiceItem> items, List<InvoicePayment> payments) {
        return Invoice.restore(
                new InvoiceId(id),
                new TenantId(tenantId),
                new CustomerId(customerId),
                new WalletId(walletId),
                Currency.of(currency.strip()),
                InvoiceOrigin.valueOf(origin),
                items,
                periodStart,
                periodEnd,
                number,
                InvoiceStatus.valueOf(status),
                settlement == null ? null : Settlement.valueOf(settlement),
                voidReason,
                createdAt,
                issuedAt,
                paidAt,
                voidedAt,
                payments);
    }
}
