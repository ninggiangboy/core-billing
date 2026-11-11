package dev.ngb.core_billing.invoicing.adapter.subscription.persistence;

import static dev.ngb.core_billing_jooq.Tables.SUBSCRIPTIONS;
import static dev.ngb.core_billing_jooq.Tables.SUBSCRIPTION_EVENTS;
import static org.jooq.impl.DSL.noCondition;

import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionHistoryEntryView;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionReadRepository;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionView;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionHistoryEntry;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing_jooq.tables.records.SubscriptionsRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;

/** Selects subscriptions and their history straight into views; no aggregate is built. */
public class JooqSubscriptionReadRepository implements SubscriptionReadRepository {

    private final DSLContext dsl;

    public JooqSubscriptionReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<SubscriptionView> list(UUID customerId, UUID walletId, SubscriptionStatus status, UUID before, int limit, List<UUID> customerIds) {
        if (customerIds != null && customerIds.isEmpty()) {
            return List.of();
        }
        Condition filter = (customerId == null ? noCondition() : SUBSCRIPTIONS.CUSTOMER_ID.eq(customerId))
                .and(walletId == null ? noCondition() : SUBSCRIPTIONS.WALLET_ID.eq(walletId))
                .and(customerIds == null ? noCondition() : SUBSCRIPTIONS.CUSTOMER_ID.in(customerIds))
                .and(status == null ? noCondition() : SUBSCRIPTIONS.STATUS.eq(status.name()))
                .and(before == null ? noCondition() : SUBSCRIPTIONS.ID.lt(before));
        return dsl.selectFrom(SUBSCRIPTIONS)
                .where(filter)
                .orderBy(SUBSCRIPTIONS.ID.desc())
                .limit(limit)
                .fetch(JooqSubscriptionReadRepository::toView);
    }

    @Override
    public Optional<SubscriptionView> findById(UUID id) {
        return dsl.selectFrom(SUBSCRIPTIONS)
                .where(SUBSCRIPTIONS.ID.eq(id))
                .fetchOptional(JooqSubscriptionReadRepository::toView);
    }

    @Override
    public List<SubscriptionHistoryEntryView> history(UUID subscriptionId) {
        return dsl.select(SUBSCRIPTION_EVENTS.ID, SUBSCRIPTION_EVENTS.TYPE, SUBSCRIPTION_EVENTS.PERIOD_NUMBER, SUBSCRIPTION_EVENTS.PERIOD_START,
                        SUBSCRIPTION_EVENTS.PERIOD_END, SUBSCRIPTION_EVENTS.AMOUNT, SUBSCRIPTIONS.CURRENCY, SUBSCRIPTION_EVENTS.CHARGE_ID,
                        SUBSCRIPTION_EVENTS.DETAIL, SUBSCRIPTION_EVENTS.OCCURRED_AT)
                .from(SUBSCRIPTION_EVENTS)
                .join(SUBSCRIPTIONS)
                .on(SUBSCRIPTIONS.ID.eq(SUBSCRIPTION_EVENTS.SUBSCRIPTION_ID).and(SUBSCRIPTIONS.TENANT_ID.eq(SUBSCRIPTION_EVENTS.TENANT_ID)))
                .where(SUBSCRIPTION_EVENTS.SUBSCRIPTION_ID.eq(subscriptionId))
                .orderBy(SUBSCRIPTION_EVENTS.OCCURRED_AT, SUBSCRIPTION_EVENTS.ID)
                .fetch(row -> new SubscriptionHistoryEntryView(
                        row.value1(),
                        SubscriptionHistoryEntry.Type.valueOf(row.value2()),
                        row.value3(),
                        row.value4(),
                        row.value5(),
                        row.value6() == null ? null : Money.of(row.value6(), Currency.of(row.value7().strip())),
                        row.value8(),
                        row.value9(),
                        row.value10()));
    }

    private static SubscriptionView toView(SubscriptionsRecord row) {
        BillingCycle cycle = BillingCycle.valueOf(row.getCycle());
        int periodsCharged = row.getPeriodsCharged();
        // Periods are counted from the anchor, so the current one is derived the same way the aggregate derives it.
        Instant start = periodsCharged == 0 ? null : cycle.plus(row.getAnchor(), periodsCharged - 1L);
        Instant end = periodsCharged == 0 ? null : cycle.plus(row.getAnchor(), periodsCharged);
        return new SubscriptionView(
                row.getId(),
                new CustomerId(row.getCustomerId()),
                new WalletId(row.getWalletId()),
                row.getPlanId(),
                row.getPlanCode(),
                cycle,
                SubscriptionStatus.valueOf(row.getStatus()),
                row.getAnchor(),
                periodsCharged,
                start,
                end,
                row.getCancelAtPeriodEnd(),
                row.getCanceledAt(),
                row.getNextRenewalAt(),
                row.getRenewalAttempts(),
                row.getCreatedAt());
    }
}
