package dev.ngb.billing.payment.domain.payment;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A payment through the Tenant's Stripe configuration: a top-up of one wallet, or the payment of one invoice.
 * It moves forward only ({@link PaymentStatus}). A top-up is SUCCEEDED only when a verified Stripe result has been
 * applied to the ledger in the same transaction, so {@code ledgerEntryId} is present exactly then. An invoice
 * payment succeeds without a ledger entry: invoicing credits the wallet from the event that reports it.
 */
public final class Payment {

    /** What an observation of Stripe's side changed here. */
    public enum Change {
        /** The payment moved forward. */
        APPLIED,
        /** Nothing to do: already there, or already further along. */
        STALE,
        /** The observation contradicts a final state. Nothing was changed; a person must look. */
        CONTRADICTION
    }

    private final PaymentId id;
    private final TenantId tenantId;
    private final StripeConfigurationId configurationId;
    private final WalletId walletId;
    private final CustomerId customerId;
    private final Money amount;
    private Money refunded;
    private PaymentStatus status;
    private String checkoutSessionId;
    private String paymentIntentId;
    private String checkoutUrl;
    private String failureReason;
    private UUID ledgerEntryId;
    private final UUID invoiceId;
    private final String idempotencyKey;
    private final String requestHash;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    private Payment(
            PaymentId id, TenantId tenantId, StripeConfigurationId configurationId, WalletId walletId, CustomerId customerId, Money amount,
            Money refunded, PaymentStatus status, String checkoutSessionId, String paymentIntentId, String checkoutUrl, String failureReason,
            UUID ledgerEntryId, UUID invoiceId, String idempotencyKey, String requestHash, Instant createdAt, Instant updatedAt, Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.configurationId = Objects.requireNonNull(configurationId, "configurationId");
        this.walletId = Objects.requireNonNull(walletId, "walletId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.refunded = Objects.requireNonNull(refunded, "refunded");
        this.status = Objects.requireNonNull(status, "status");
        this.checkoutSessionId = checkoutSessionId;
        this.paymentIntentId = paymentIntentId;
        this.checkoutUrl = checkoutUrl;
        this.failureReason = failureReason;
        this.ledgerEntryId = ledgerEntryId;
        this.invoiceId = invoiceId;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.completedAt = completedAt;
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a payment must be positive");
        }
    }

    public static Payment start(
            PaymentId id, StripeConfiguration configuration, WalletId walletId, CustomerId customerId, Money amount, String idempotencyKey,
            String requestHash, Instant now) {
        return new Payment(id, configuration.tenantId(), configuration.id(), walletId, customerId, amount, Money.zero(amount.currency()),
                PaymentStatus.REQUIRES_ACTION, null, null, null, null, null, null, idempotencyKey, requestHash, now, now, null);
    }

    /**
     * A payment of one invoice, through the same configuration and checkout as a top-up. The wallet is the invoice's:
     * it is credited by invoicing when this payment succeeds, not by the payment context.
     */
    public static Payment startForInvoice(
            PaymentId id, StripeConfiguration configuration, UUID invoiceId, WalletId walletId, CustomerId customerId, Money amount,
            String idempotencyKey, String requestHash, Instant now) {
        return new Payment(id, configuration.tenantId(), configuration.id(), walletId, customerId, amount, Money.zero(amount.currency()),
                PaymentStatus.REQUIRES_ACTION, null, null, null, null, null, Objects.requireNonNull(invoiceId, "invoiceId"), idempotencyKey, requestHash,
                now, now, null);
    }

    public static Payment restore(
            PaymentId id, TenantId tenantId, StripeConfigurationId configurationId, WalletId walletId, CustomerId customerId, Money amount,
            Money refunded, PaymentStatus status, String checkoutSessionId, String paymentIntentId, String checkoutUrl, String failureReason,
            UUID ledgerEntryId, UUID invoiceId, String idempotencyKey, String requestHash, Instant createdAt, Instant updatedAt, Instant completedAt) {
        return new Payment(id, tenantId, configurationId, walletId, customerId, amount, refunded, status, checkoutSessionId, paymentIntentId,
                checkoutUrl, failureReason, ledgerEntryId, invoiceId, idempotencyKey, requestHash, createdAt, updatedAt, completedAt);
    }

    /** Remembers the Stripe objects once Stripe has created them. Safe to repeat with the same values. */
    public void attachCheckout(String sessionId, String url, Instant now) {
        if (this.checkoutSessionId != null && !this.checkoutSessionId.equals(sessionId)) {
            throw new IllegalStateException("the payment already has a different checkout session");
        }
        this.checkoutSessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.checkoutUrl = url;
        this.updatedAt = now;
    }

    /** The first intent wins: once known, a different intent id is a contradiction, not an update. */
    public boolean attachIntent(String intentId, Instant now) {
        if (intentId == null || intentId.equals(paymentIntentId)) {
            return true;
        }
        if (paymentIntentId != null) {
            return false;
        }
        this.paymentIntentId = intentId;
        this.updatedAt = now;
        return true;
    }

    public Change markProcessing(Instant now) {
        return move(PaymentStatus.PROCESSING, null, now);
    }

    /** Applies a verified success. The caller credits the wallet and then records the entry with {@link #recordLedgerEntry}. */
    public Change succeed() {
        if (status == PaymentStatus.SUCCEEDED) {
            return Change.STALE;
        }
        if (!status.canMoveTo(PaymentStatus.SUCCEEDED)) {
            return Change.CONTRADICTION;
        }
        return Change.APPLIED;
    }

    public void recordLedgerEntry(UUID entryId, Instant now) {
        if (status.isFinal() && status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("a " + status + " payment cannot be credited");
        }
        this.ledgerEntryId = Objects.requireNonNull(entryId, "entryId");
        this.status = PaymentStatus.SUCCEEDED;
        this.failureReason = null;
        this.completedAt = completedAt == null ? now : completedAt;
        this.updatedAt = now;
    }

    /** True for a payment of an invoice, which never credits the wallet itself. */
    public boolean isForInvoice() {
        return invoiceId != null;
    }

    /**
     * Records that the money for an invoice arrived. The wallet is credited by invoicing from the
     * {@code payment.succeeded} event this success announces, so the payment carries no ledger entry.
     */
    public void recordInvoiceSettlement(Instant now) {
        if (!isForInvoice()) {
            throw new IllegalStateException("only an invoice payment is settled without a ledger entry");
        }
        if (status.isFinal() && status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("a " + status + " payment cannot be settled");
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.failureReason = null;
        this.completedAt = completedAt == null ? now : completedAt;
        this.updatedAt = now;
    }

    public Change fail(String reason, Instant now) {
        return move(PaymentStatus.FAILED, reason, now);
    }

    public Change cancel(Instant now) {
        return move(PaymentStatus.CANCELED, null, now);
    }

    private Change move(PaymentStatus next, String reason, Instant now) {
        if (status == next) {
            return Change.STALE;
        }
        if (!status.canMoveTo(next)) {
            // Behind or equal in rank is a harmless straggler; a different final state is a contradiction.
            return status.isFinal() && next.isFinal() ? Change.CONTRADICTION : Change.STALE;
        }
        this.status = next;
        this.failureReason = reason;
        if (next.isFinal()) {
            this.completedAt = now;
        }
        this.updatedAt = now;
        return Change.APPLIED;
    }

    /** Money that can still be sent back, ignoring refunds in flight. */
    public Money refundable() {
        return amount.minus(refunded);
    }

    public void addRefunded(Money delta, Instant now) {
        Money next = refunded.plus(delta);
        if (next.isGreaterThan(amount)) {
            throw new IllegalStateException("refunds would exceed the captured amount");
        }
        this.refunded = next;
        this.updatedAt = now;
    }

    public PaymentId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public StripeConfigurationId configurationId() {
        return configurationId;
    }

    public WalletId walletId() {
        return walletId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public Money amount() {
        return amount;
    }

    public Money refunded() {
        return refunded;
    }

    public PaymentStatus status() {
        return status;
    }

    public String checkoutSessionId() {
        return checkoutSessionId;
    }

    public String paymentIntentId() {
        return paymentIntentId;
    }

    public String checkoutUrl() {
        return checkoutUrl;
    }

    public String failureReason() {
        return failureReason;
    }

    public UUID ledgerEntryId() {
        return ledgerEntryId;
    }

    /** The invoice this payment settles, or null for a top-up. */
    public UUID invoiceId() {
        return invoiceId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
