package dev.ngb.billing.invoicing.domain.invoice;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A request for payment of what one wallet already owes. The charges it lists have been taken from the wallet
 * (for a Postpaid wallet that is the debt), so paying the invoice credits the wallet by its total and never
 * changes what was charged. It is PAID only by a wallet that already paid the charge or by a verified payment;
 * nothing else moves it there.
 */
public final class Invoice {

    /** What a payment result did to the invoice. */
    public enum Change {
        /** The invoice moved from unpaid to paid. */
        PAID,
        /** The payment is recorded, but the invoice was not open: it was already paid, void, or still a draft. */
        RECORDED_ONLY,
        /** Nothing to do: this payment was already settled. */
        UNCHANGED
    }

    private final InvoiceId id;
    private final TenantId tenantId;
    private final CustomerId customerId;
    private final WalletId walletId;
    private final Currency currency;
    private final InvoiceOrigin origin;
    private final List<InvoiceItem> items;
    private final Money total;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final Instant createdAt;
    private final List<InvoicePayment> payments;
    private String number;
    private InvoiceStatus status;
    private Settlement settlement;
    private String voidReason;
    private Instant issuedAt;
    private Instant paidAt;
    private Instant voidedAt;

    private Invoice(
            InvoiceId id, TenantId tenantId, CustomerId customerId, WalletId walletId, Currency currency, InvoiceOrigin origin, List<InvoiceItem> items,
            Instant periodStart, Instant periodEnd, String number, InvoiceStatus status, Settlement settlement, String voidReason, Instant createdAt,
            Instant issuedAt, Instant paidAt, Instant voidedAt, List<InvoicePayment> payments) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.walletId = Objects.requireNonNull(walletId, "walletId");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.items = List.copyOf(items);
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.number = number;
        this.status = Objects.requireNonNull(status, "status");
        this.settlement = settlement;
        this.voidReason = voidReason;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.issuedAt = issuedAt;
        this.paidAt = paidAt;
        this.voidedAt = voidedAt;
        this.payments = new ArrayList<>(payments);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("an invoice needs at least one line");
        }
        Money sum = Money.zero(currency);
        for (InvoiceItem item : items) {
            if (!item.amount().currency().equals(currency)) {
                throw new IllegalArgumentException("every line must be in the invoice currency " + currency);
            }
            sum = sum.plus(item.amount());
        }
        this.total = sum;
        if ((periodStart == null) != (periodEnd == null) || periodStart != null && !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("an invoice period needs both ends, in order");
        }
    }

    /** A new invoice being assembled. It has no number and is not payable until {@link #issue}. */
    public static Invoice draft(
            InvoiceId id, TenantId tenantId, CustomerId customerId, WalletId walletId, Currency currency, InvoiceOrigin origin, List<InvoiceItem> items,
            Instant periodStart, Instant periodEnd, Instant now) {
        return new Invoice(id, tenantId, customerId, walletId, currency, origin, items, periodStart, periodEnd, null, InvoiceStatus.DRAFT, null, null,
                now, null, null, null, List.of());
    }

    public static Invoice restore(
            InvoiceId id, TenantId tenantId, CustomerId customerId, WalletId walletId, Currency currency, InvoiceOrigin origin, List<InvoiceItem> items,
            Instant periodStart, Instant periodEnd, String number, InvoiceStatus status, Settlement settlement, String voidReason, Instant createdAt,
            Instant issuedAt, Instant paidAt, Instant voidedAt, List<InvoicePayment> payments) {
        return new Invoice(id, tenantId, customerId, walletId, currency, origin, items, periodStart, periodEnd, number, status, settlement, voidReason,
                createdAt, issuedAt, paidAt, voidedAt, payments);
    }

    /** Numbers the invoice and opens it for payment. */
    public void issue(String invoiceNumber, Instant now) {
        requireStatus(InvoiceStatus.DRAFT, "issued");
        this.number = Objects.requireNonNull(invoiceNumber, "invoiceNumber");
        this.status = InvoiceStatus.OPEN;
        this.issuedAt = now;
    }

    /** The wallet already paid every charge on this invoice, so it is a paid record from the start. */
    public void settleFromWallet(Instant now) {
        requireStatus(InvoiceStatus.OPEN, "settled from the wallet");
        this.status = InvoiceStatus.PAID;
        this.settlement = Settlement.WALLET;
        this.paidAt = now;
    }

    /** Remembers that a payment was started for the whole invoice. More than one may be in flight; the first to succeed pays it. */
    public void startPayment(UUID paymentId, Instant now) {
        if (!status.payable()) {
            throw new IllegalStateException("a " + status + " invoice cannot be paid");
        }
        if (payments.stream().anyMatch(p -> p.paymentId().equals(paymentId))) {
            return;
        }
        payments.add(InvoicePayment.started(paymentId, total, now));
    }

    /**
     * Applies a verified payment success. The caller credits the wallet and passes the ledger entry. A success that
     * arrives for an invoice that is not payable (already paid, or void) is still recorded, because the money is real.
     */
    public Change recordPaymentSucceeded(UUID paymentId, UUID ledgerEntryId, Instant now) {
        int index = indexOf(paymentId);
        if (index >= 0 && payments.get(index).status() == InvoicePayment.Status.SUCCEEDED) {
            return Change.UNCHANGED;
        }
        InvoicePayment started = index >= 0 ? payments.get(index) : InvoicePayment.started(paymentId, total, now);
        InvoicePayment settled = started.succeeded(ledgerEntryId, now);
        if (index >= 0) {
            payments.set(index, settled);
        } else {
            payments.add(settled);
        }
        if (!status.payable()) {
            return Change.RECORDED_ONLY;
        }
        this.status = InvoiceStatus.PAID;
        this.settlement = Settlement.PAYMENT;
        this.paidAt = now;
        return Change.PAID;
    }

    /** A failed payment is history only: the invoice stays open and can be paid again. */
    public Change recordPaymentFailed(UUID paymentId, String reason, Instant now) {
        int index = indexOf(paymentId);
        if (index < 0 || payments.get(index).status() != InvoicePayment.Status.STARTED) {
            return Change.UNCHANGED;
        }
        payments.set(index, payments.get(index).failed(reason, now));
        return Change.RECORDED_ONLY;
    }

    /** Cancels the request for payment. The wallet is not touched: what was charged stays charged. A paid invoice cannot be voided. */
    public void voidInvoice(String reason, Instant now) {
        if (status != InvoiceStatus.DRAFT && status != InvoiceStatus.OPEN && status != InvoiceStatus.UNCOLLECTIBLE) {
            throw new IllegalStateException("a " + status + " invoice cannot be voided");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("a reason of 1 to 500 characters is required to void an invoice");
        }
        this.status = InvoiceStatus.VOID;
        this.voidReason = reason;
        this.voidedAt = now;
    }

    /** The debt is written off as unlikely to be paid. It can still be paid later. */
    public void markUncollectible() {
        requireStatus(InvoiceStatus.OPEN, "marked uncollectible");
        this.status = InvoiceStatus.UNCOLLECTIBLE;
    }

    private void requireStatus(InvoiceStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("only a %s invoice can be %s; this one is %s".formatted(expected, action, status));
        }
    }

    private int indexOf(UUID paymentId) {
        for (int i = 0; i < payments.size(); i++) {
            if (payments.get(i).paymentId().equals(paymentId)) {
                return i;
            }
        }
        return -1;
    }

    public InvoiceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public WalletId walletId() {
        return walletId;
    }

    public Currency currency() {
        return currency;
    }

    public InvoiceOrigin origin() {
        return origin;
    }

    public List<InvoiceItem> items() {
        return items;
    }

    public Money total() {
        return total;
    }

    public Instant periodStart() {
        return periodStart;
    }

    public Instant periodEnd() {
        return periodEnd;
    }

    public String number() {
        return number;
    }

    public InvoiceStatus status() {
        return status;
    }

    public Settlement settlement() {
        return settlement;
    }

    public String voidReason() {
        return voidReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant paidAt() {
        return paidAt;
    }

    public Instant voidedAt() {
        return voidedAt;
    }

    public List<InvoicePayment> payments() {
        return Collections.unmodifiableList(payments);
    }
}
