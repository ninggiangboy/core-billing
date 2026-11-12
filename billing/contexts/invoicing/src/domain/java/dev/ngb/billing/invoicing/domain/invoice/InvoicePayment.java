package dev.ngb.billing.invoicing.domain.invoice;

import dev.ngb.billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One attempt to pay an invoice through a payment: the history of how the invoice was, or was not, paid. */
public record InvoicePayment(UUID paymentId, Money amount, Status status, String failureReason, UUID ledgerEntryId, Instant startedAt, Instant settledAt) {

    public enum Status { STARTED, SUCCEEDED, FAILED }

    public InvoicePayment {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
    }

    static InvoicePayment started(UUID paymentId, Money amount, Instant now) {
        return new InvoicePayment(paymentId, amount, Status.STARTED, null, null, now, null);
    }

    InvoicePayment succeeded(UUID entryId, Instant now) {
        return new InvoicePayment(paymentId, amount, Status.SUCCEEDED, null, entryId, startedAt, now);
    }

    InvoicePayment failed(String reason, Instant now) {
        return new InvoicePayment(paymentId, amount, Status.FAILED, reason, null, startedAt, now);
    }
}
