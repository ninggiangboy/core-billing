package dev.ngb.billing.invoicing.application.invoice.query;

import dev.ngb.billing.invoicing.domain.invoice.InvoicePayment;
import dev.ngb.billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.UUID;

/** One attempt to pay an invoice: how it was, or was not, paid. */
public record InvoicePaymentView(
        UUID paymentId, Money amount, InvoicePayment.Status status, String failureReason, UUID ledgerEntryId, Instant startedAt, Instant settledAt) {}
