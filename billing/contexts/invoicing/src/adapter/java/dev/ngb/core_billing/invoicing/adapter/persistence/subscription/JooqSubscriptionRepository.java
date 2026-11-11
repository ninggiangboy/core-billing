package dev.ngb.core_billing.invoicing.adapter.persistence.subscription;

import static dev.ngb.core_billing_jooq.Tables.SUBSCRIPTIONS;
import static dev.ngb.core_billing_jooq.Tables.SUBSCRIPTION_EVENTS;

import dev.ngb.core_billing.invoicing.application.subscription.port.SubscriptionRepository;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionEvent;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import dev.ngb.core_billing_jooq.tables.records.SubscriptionsRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqSubscriptionRepository implements SubscriptionRepository {

    private final DSLContext dsl;

    public JooqSubscriptionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(Subscription subscription) {
        try {
            dsl.insertInto(SUBSCRIPTIONS)
                    .set(SUBSCRIPTIONS.ID, subscription.id().value())
                    .set(SUBSCRIPTIONS.TENANT_ID, subscription.tenantId().value())
                    .set(SUBSCRIPTIONS.CUSTOMER_ID, subscription.customerId().value())
                    .set(SUBSCRIPTIONS.WALLET_ID, subscription.walletId().value())
                    .set(SUBSCRIPTIONS.CURRENCY, subscription.currency().code())
                    .set(SUBSCRIPTIONS.PLAN_ID, subscription.planId())
                    .set(SUBSCRIPTIONS.PLAN_CODE, subscription.planCode())
                    .set(SUBSCRIPTIONS.CYCLE, subscription.cycle().name())
                    .set(SUBSCRIPTIONS.ANCHOR, subscription.anchor())
                    .set(SUBSCRIPTIONS.PERIODS_CHARGED, subscription.periodsCharged())
                    .set(SUBSCRIPTIONS.STATUS, subscription.status().name())
                    .set(SUBSCRIPTIONS.CANCEL_AT_PERIOD_END, subscription.cancelAtPeriodEnd())
                    .set(SUBSCRIPTIONS.CANCELED_AT, subscription.canceledAt())
                    .set(SUBSCRIPTIONS.NEXT_RENEWAL_AT, subscription.nextRenewalAt())
                    .set(SUBSCRIPTIONS.RENEWAL_ATTEMPTS, subscription.renewalAttempts())
                    .set(SUBSCRIPTIONS.CREATED_AT, subscription.createdAt())
                    .set(SUBSCRIPTIONS.UPDATED_AT, subscription.updatedAt())
                    .set(SUBSCRIPTIONS.IDEMPOTENCY_KEY, subscription.idempotencyKey())
                    .set(SUBSCRIPTIONS.REQUEST_HASH, subscription.requestHash())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("this idempotency key was already used for a subscription");
            }
            throw e;
        }
    }

    @Override
    public void update(Subscription subscription) {
        dsl.update(SUBSCRIPTIONS)
                .set(SUBSCRIPTIONS.CUSTOMER_ID, subscription.customerId().value())
                .set(SUBSCRIPTIONS.WALLET_ID, subscription.walletId().value())
                .set(SUBSCRIPTIONS.CURRENCY, subscription.currency().code())
                .set(SUBSCRIPTIONS.PLAN_ID, subscription.planId())
                .set(SUBSCRIPTIONS.PLAN_CODE, subscription.planCode())
                .set(SUBSCRIPTIONS.CYCLE, subscription.cycle().name())
                .set(SUBSCRIPTIONS.ANCHOR, subscription.anchor())
                .set(SUBSCRIPTIONS.PERIODS_CHARGED, subscription.periodsCharged())
                .set(SUBSCRIPTIONS.STATUS, subscription.status().name())
                .set(SUBSCRIPTIONS.CANCEL_AT_PERIOD_END, subscription.cancelAtPeriodEnd())
                .set(SUBSCRIPTIONS.CANCELED_AT, subscription.canceledAt())
                .set(SUBSCRIPTIONS.NEXT_RENEWAL_AT, subscription.nextRenewalAt())
                .set(SUBSCRIPTIONS.RENEWAL_ATTEMPTS, subscription.renewalAttempts())
                .set(SUBSCRIPTIONS.UPDATED_AT, subscription.updatedAt())
                .set(SUBSCRIPTIONS.IDEMPOTENCY_KEY, subscription.idempotencyKey())
                .set(SUBSCRIPTIONS.REQUEST_HASH, subscription.requestHash())
                .where(SUBSCRIPTIONS.ID.eq(subscription.id().value()))
                .execute();
    }

    @Override
    public Optional<Subscription> findById(SubscriptionId id) {
        return dsl.selectFrom(SUBSCRIPTIONS)
                .where(SUBSCRIPTIONS.ID.eq(id.value()))
                .fetchOptional(JooqSubscriptionRepository::toDomain);
    }

    @Override
    public Optional<Subscription> lockById(SubscriptionId id) {
        return dsl.selectFrom(SUBSCRIPTIONS)
                .where(SUBSCRIPTIONS.ID.eq(id.value()))
                .forUpdate()
                .fetchOptional(JooqSubscriptionRepository::toDomain);
    }

    @Override
    public Optional<Subscription> lockNextDue(Instant now, Set<UUID> excluding) {
        Condition due = SUBSCRIPTIONS.STATUS.in("ACTIVE", "PAST_DUE")
                .and(SUBSCRIPTIONS.NEXT_RENEWAL_AT.le(now));
        if (!excluding.isEmpty()) {
            due = due.and(SUBSCRIPTIONS.ID.notIn(excluding));
        }
        return dsl.selectFrom(SUBSCRIPTIONS)
                .where(due)
                .orderBy(SUBSCRIPTIONS.NEXT_RENEWAL_AT, SUBSCRIPTIONS.ID)
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOptional(JooqSubscriptionRepository::toDomain);
    }

    @Override
    public Optional<Subscription> findByIdempotencyKey(String idempotencyKey) {
        return dsl.selectFrom(SUBSCRIPTIONS)
                .where(SUBSCRIPTIONS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(JooqSubscriptionRepository::toDomain);
    }

    @Override
    public void appendEvent(SubscriptionEvent event) {
        dsl.insertInto(SUBSCRIPTION_EVENTS)
                .set(SUBSCRIPTION_EVENTS.ID, event.id())
                .set(SUBSCRIPTION_EVENTS.TENANT_ID, event.tenantId().value())
                .set(SUBSCRIPTION_EVENTS.SUBSCRIPTION_ID, event.subscriptionId().value())
                .set(SUBSCRIPTION_EVENTS.TYPE, event.type().name())
                .set(SUBSCRIPTION_EVENTS.PERIOD_NUMBER, event.periodNumber())
                .set(SUBSCRIPTION_EVENTS.PERIOD_START, event.periodStart())
                .set(SUBSCRIPTION_EVENTS.PERIOD_END, event.periodEnd())
                .set(SUBSCRIPTION_EVENTS.AMOUNT, event.amount() == null ? null : event.amount().minorUnits())
                .set(SUBSCRIPTION_EVENTS.CHARGE_ID, event.chargeId())
                .set(SUBSCRIPTION_EVENTS.DETAIL, event.detail())
                .set(SUBSCRIPTION_EVENTS.OCCURRED_AT, event.occurredAt())
                .execute();
    }

    private static Subscription toDomain(SubscriptionsRecord row) {
        return Subscription.restore(
                new SubscriptionId(row.getId()),
                new TenantId(row.getTenantId()),
                new CustomerId(row.getCustomerId()),
                new WalletId(row.getWalletId()),
                Currency.of(row.getCurrency().strip()),
                row.getPlanId(),
                row.getPlanCode(),
                BillingCycle.valueOf(row.getCycle()),
                row.getAnchor(),
                row.getPeriodsCharged(),
                SubscriptionStatus.valueOf(row.getStatus()),
                row.getCancelAtPeriodEnd(),
                row.getCanceledAt(),
                row.getNextRenewalAt(),
                row.getRenewalAttempts(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getIdempotencyKey(),
                row.getRequestHash());
    }
}
