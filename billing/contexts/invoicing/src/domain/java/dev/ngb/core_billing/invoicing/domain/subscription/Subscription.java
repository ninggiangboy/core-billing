package dev.ngb.core_billing.invoicing.domain.subscription;

import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
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

    /** True when a period is due: the subscription is not canceled and a renewal time is set and has come. */
    public boolean dueAt(Instant now) {
        return status != SubscriptionStatus.CANCELED && nextRenewalAt != null && !nextRenewalAt.isAfter(now);
    }

    /** The number of the period to charge next. */
    public int periodToCharge() {
        return periodsCharged;
    }

    /** Which try at charging the next period this is, starting at 1; part of the charge's idempotency key. */
    public int nextAttempt() {
        return renewalAttempts + 1;
    }

    /** Records that the next period was charged: the subscription is active, paid up to the end of that period, and due again there. */
    public void recordRenewed(Instant now) {
        if (status == SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("a canceled subscription does not renew");
        }
        int charged = periodsCharged;
        this.periodsCharged = charged + 1;
        this.status = SubscriptionStatus.ACTIVE;
        this.renewalAttempts = 0;
        this.nextRenewalAt = periodEnd(charged);
        this.updatedAt = now;
    }

    /**
     * The next period could not be charged. The subscription is past due, the attempt is counted, and it is looked at
     * again at {@code retryAt}, or never automatically when {@code retryAt} is null because the attempts ran out. It is
     * not canceled and nothing is stopped for the Tenant: that decision stays with the Tenant System.
     */
    public void recordPaymentFailed(Instant retryAt, Instant now) {
        if (status == SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("a canceled subscription does not renew");
        }
        this.status = SubscriptionStatus.PAST_DUE;
        this.renewalAttempts = renewalAttempts + 1;
        this.nextRenewalAt = retryAt;
        this.updatedAt = now;
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
