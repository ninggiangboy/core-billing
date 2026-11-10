package dev.ngb.core_billing.payment.domain.refund;

import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;

/**
 * Money sent back to a customer's card. While Stripe works the unspent balance is held ({@code reservationId}),
 * and the ledger changes only once Stripe reports the refund succeeded.
 */
public final class Refund {

    private final RefundId id;
    private final TenantId tenantId;
    private final PaymentId paymentId;
    private final Money amount;
    private RefundStatus status;
    private final String reason;
    private String stripeRefundId;
    private final UUID reservationId;
    private UUID ledgerEntryId;
    private String failureReason;
    private final String actor;
    private final String idempotencyKey;
    private final String requestHash;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    private Refund(
            RefundId id, TenantId tenantId, PaymentId paymentId, Money amount, RefundStatus status, String reason, String stripeRefundId,
            UUID reservationId, UUID ledgerEntryId, String failureReason, String actor, String idempotencyKey, String requestHash,
            Instant createdAt, Instant updatedAt, Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.status = Objects.requireNonNull(status, "status");
        this.reason = reason;
        this.stripeRefundId = stripeRefundId;
        this.reservationId = reservationId;
        this.ledgerEntryId = ledgerEntryId;
        this.failureReason = failureReason;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.completedAt = completedAt;
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a refund must be positive");
        }
        if (reason != null && reason.length() > 500) {
            throw new IllegalArgumentException("the reason is limited to 500 characters");
        }
    }

    public static Refund request(
            RefundId id, Payment payment, Money amount, String reason, UUID reservationId, String actor, String idempotencyKey, String requestHash,
            Instant now) {
        if (payment.status() != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("only a succeeded payment can be refunded");
        }
        return new Refund(id, payment.tenantId(), payment.id(), amount, RefundStatus.PENDING, blankToNull(reason), null, reservationId, null, null,
                actor, idempotencyKey, requestHash, now, now, null);
    }

    public static Refund restore(
            RefundId id, TenantId tenantId, PaymentId paymentId, Money amount, RefundStatus status, String reason, String stripeRefundId,
            UUID reservationId, UUID ledgerEntryId, String failureReason, String actor, String idempotencyKey, String requestHash,
            Instant createdAt, Instant updatedAt, Instant completedAt) {
        return new Refund(id, tenantId, paymentId, amount, status, reason, stripeRefundId, reservationId, ledgerEntryId, failureReason, actor,
                idempotencyKey, requestHash, createdAt, updatedAt, completedAt);
    }

    public void attachStripeRefund(String stripeId, Instant now) {
        if (this.stripeRefundId != null && !this.stripeRefundId.equals(stripeId)) {
            throw new IllegalStateException("the refund already has a different Stripe refund");
        }
        this.stripeRefundId = Objects.requireNonNull(stripeId, "stripeId");
        this.updatedAt = now;
    }

    /** @return false when the refund was already final (nothing changed) */
    public boolean succeed(UUID entryId, Instant now) {
        if (status.isFinal()) {
            return false;
        }
        this.status = RefundStatus.SUCCEEDED;
        this.ledgerEntryId = Objects.requireNonNull(entryId, "entryId");
        this.completedAt = now;
        this.updatedAt = now;
        return true;
    }

    /** @return false when the refund was already final (nothing changed) */
    public boolean fail(String why, Instant now) {
        if (status.isFinal()) {
            return false;
        }
        this.status = RefundStatus.FAILED;
        this.failureReason = why;
        this.completedAt = now;
        this.updatedAt = now;
        return true;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    public RefundId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PaymentId paymentId() {
        return paymentId;
    }

    public Money amount() {
        return amount;
    }

    public RefundStatus status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public String stripeRefundId() {
        return stripeRefundId;
    }

    public UUID reservationId() {
        return reservationId;
    }

    public UUID ledgerEntryId() {
        return ledgerEntryId;
    }

    public String failureReason() {
        return failureReason;
    }

    public String actor() {
        return actor;
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
