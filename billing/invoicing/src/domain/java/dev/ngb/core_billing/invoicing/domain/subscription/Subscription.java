package dev.ngb.core_billing.invoicing.domain.subscription;

import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recurring plan for one customer, paid from one wallet. It is a schedule of period charges counted from an
 * anchor: period {@code n} runs from {@code anchor + n cycles} to {@code anchor + (n+1) cycles} and is charged in
 * advance when it starts, so the subscription knows how many periods it has charged and when the next is due. It
 * decides nothing for the Tenant System: it only reports renewals and failures.
 */
public final class Subscription {

    /** What cancelling did. */
    public enum Cancellation {
        /** Nothing had been paid for the current time, so it ended at once. */
        CANCELED_NOW,
        /** The paid period runs to its end and the subscription then ends instead of renewing. */
        SCHEDULED,
        /** It was already canceled, or already scheduled to end. */
        UNCHANGED
    }

    private final SubscriptionId id;
    private final TenantId tenantId;
    private final CustomerId customerId;
    private final WalletId walletId;
    private final Currency currency;
    private final UUID planId;
    private final String planCode;
    private final BillingCycle cycle;
    private final Instant anchor;
    private final Instant createdAt;
    private final String idempotencyKey;
    private final String requestHash;
    private int periodsCharged;
    private SubscriptionStatus status;
    private boolean cancelAtPeriodEnd;
    private Instant canceledAt;
    private Instant nextRenewalAt;
    private int renewalAttempts;
    private Instant updatedAt;

    private Subscription(
            SubscriptionId id, TenantId tenantId, CustomerId customerId, WalletId walletId, Currency currency, UUID planId, String planCode,
            BillingCycle cycle, Instant anchor, int periodsCharged, SubscriptionStatus status, boolean cancelAtPeriodEnd, Instant canceledAt,
            Instant nextRenewalAt, int renewalAttempts, Instant createdAt, Instant updatedAt, String idempotencyKey, String requestHash) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.walletId = Objects.requireNonNull(walletId, "walletId");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.planId = Objects.requireNonNull(planId, "planId");
        this.planCode = Objects.requireNonNull(planCode, "planCode");
        this.cycle = Objects.requireNonNull(cycle, "cycle");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.periodsCharged = periodsCharged;
        this.status = Objects.requireNonNull(status, "status");
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.canceledAt = canceledAt;
        this.nextRenewalAt = nextRenewalAt;
        this.renewalAttempts = renewalAttempts;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        if (periodsCharged < 0 || renewalAttempts < 0) {
            throw new IllegalArgumentException("counters cannot be negative");
        }
    }

    /** A new subscription anchored at {@code now}. Its first period is due at once; the caller charges it. */
    public static Subscription create(
            SubscriptionId id, TenantId tenantId, CustomerId customerId, WalletId walletId, Currency currency, UUID planId, String planCode, BillingCycle cycle,
            String idempotencyKey, String requestHash, Instant now) {
        return new Subscription(id, tenantId, customerId, walletId, currency, planId, planCode, cycle, now, 0, SubscriptionStatus.ACTIVE, false, null, now, 0,
                now, now, idempotencyKey, requestHash);
    }

    public static Subscription restore(
            SubscriptionId id, TenantId tenantId, CustomerId customerId, WalletId walletId, Currency currency, UUID planId, String planCode, BillingCycle cycle,
            Instant anchor, int periodsCharged, SubscriptionStatus status, boolean cancelAtPeriodEnd, Instant canceledAt, Instant nextRenewalAt,
            int renewalAttempts, Instant createdAt, Instant updatedAt, String idempotencyKey, String requestHash) {
        return new Subscription(id, tenantId, customerId, walletId, currency, planId, planCode, cycle, anchor, periodsCharged, status, cancelAtPeriodEnd,
                canceledAt, nextRenewalAt, renewalAttempts, createdAt, updatedAt, idempotencyKey, requestHash);
    }

    /** Start of period {@code number}, counted from the anchor so month ends never drift. */
    public Instant periodStart(int number) {
        return cycle.plus(anchor, number);
    }

    public Instant periodEnd(int number) {
        return cycle.plus(anchor, (long) number + 1);
    }

    /** Start of the period that has been paid for most recently, or null before the first charge. */
    public Instant currentPeriodStart() {
        return periodsCharged == 0 ? null : periodStart(periodsCharged - 1);
    }

    /** End of the period that has been paid for most recently, or null before the first charge. */
    public Instant currentPeriodEnd() {
        return periodsCharged == 0 ? null : periodEnd(periodsCharged - 1);
    }

    /**
     * Ends the subscription now when no paid period is running (nothing was charged yet, or the last period could
     * not be paid), otherwise schedules it to end where the paid period ends. Cancelling twice changes nothing.
     */
    public Cancellation requestCancel(Instant now) {
        if (status == SubscriptionStatus.CANCELED || cancelAtPeriodEnd) {
            return Cancellation.UNCHANGED;
        }
        if (periodsCharged == 0 || status == SubscriptionStatus.PAST_DUE) {
            cancelNow(now);
            return Cancellation.CANCELED_NOW;
        }
        this.cancelAtPeriodEnd = true;
        this.updatedAt = now;
        return Cancellation.SCHEDULED;
    }

    /** Ends the subscription; it never renews again. */
    public void cancelNow(Instant now) {
        if (status == SubscriptionStatus.CANCELED) {
            return;
        }
        this.status = SubscriptionStatus.CANCELED;
        this.cancelAtPeriodEnd = false;
        this.canceledAt = now;
        this.nextRenewalAt = null;
        this.updatedAt = now;
    }

    public SubscriptionId id() {
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

    public UUID planId() {
        return planId;
    }

    public String planCode() {
        return planCode;
    }

    public BillingCycle cycle() {
        return cycle;
    }

    public Instant anchor() {
        return anchor;
    }

    public int periodsCharged() {
        return periodsCharged;
    }

    public SubscriptionStatus status() {
        return status;
    }

    public boolean cancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public Instant canceledAt() {
        return canceledAt;
    }

    public Instant nextRenewalAt() {
        return nextRenewalAt;
    }

    public int renewalAttempts() {
        return renewalAttempts;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }
}
