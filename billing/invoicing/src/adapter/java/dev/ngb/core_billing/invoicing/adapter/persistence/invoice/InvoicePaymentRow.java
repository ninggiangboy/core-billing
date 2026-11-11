package dev.ngb.core_billing.invoicing.adapter.persistence.invoice;

import dev.ngb.core_billing.invoicing.domain.invoice.InvoicePayment;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("invoice_payments")
record InvoicePaymentRow(
        @Id UUID paymentId,
        UUID tenantId,
        UUID invoiceId,
        String currency,
        long amount,
        String status,
        String failureReason,
        UUID ledgerEntryId,
        Instant startedAt,
        Instant settledAt) {

    static InvoicePaymentRow from(UUID tenantId, UUID invoiceId, InvoicePayment payment) {
        return new InvoicePaymentRow(payment.paymentId(), tenantId, invoiceId, payment.amount().currency().code(), payment.amount().minorUnits(),
                payment.status().name(), payment.failureReason(), payment.ledgerEntryId(), payment.startedAt(), payment.settledAt());
    }

    InvoicePayment toDomain() {
        return new InvoicePayment(paymentId, Money.of(amount, Currency.of(currency.strip())), InvoicePayment.Status.valueOf(status), failureReason,
                ledgerEntryId, startedAt, settledAt);
    }
}
