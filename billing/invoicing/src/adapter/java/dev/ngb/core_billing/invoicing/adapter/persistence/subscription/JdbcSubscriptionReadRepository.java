package dev.ngb.core_billing.invoicing.adapter.persistence.subscription;

import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionEventView;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionReadRepository;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionView;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionEvent;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects subscriptions and their history straight into views; no aggregate is built. */
public class JdbcSubscriptionReadRepository implements SubscriptionReadRepository {

    private static final String COLUMNS = "id, customer_id, wallet_id, plan_id, plan_code, cycle, status, anchor, periods_charged, cancel_at_period_end, "
            + "canceled_at, next_renewal_at, renewal_attempts, created_at";

    private record SubscriptionRowView(
            UUID id,
            UUID customerId,
            UUID walletId,
            UUID planId,
            String planCode,
            String cycle,
            String status,
            Instant anchor,
            int periodsCharged,
            boolean cancelAtPeriodEnd,
            Instant canceledAt,
            Instant nextRenewalAt,
            int renewalAttempts,
            Instant createdAt) {

        SubscriptionView toView() {
            BillingCycle billingCycle = BillingCycle.valueOf(cycle);
            // Periods are counted from the anchor, so the current one is derived the same way the aggregate derives it.
            Instant start = periodsCharged == 0 ? null : billingCycle.plus(anchor, periodsCharged - 1L);
            Instant end = periodsCharged == 0 ? null : billingCycle.plus(anchor, periodsCharged);
            return new SubscriptionView(id, new CustomerId(customerId), new WalletId(walletId), planId, planCode, billingCycle, SubscriptionStatus.valueOf(status),
                    anchor, periodsCharged, start, end, cancelAtPeriodEnd, canceledAt, nextRenewalAt, renewalAttempts, createdAt);
        }
    }

    private record EventRowView(
            UUID id, String type, Integer periodNumber, Instant periodStart, Instant periodEnd, Long amount, String currency, UUID chargeId, String detail,
            Instant occurredAt) {

        SubscriptionEventView toView() {
            return new SubscriptionEventView(id, SubscriptionEvent.Type.valueOf(type), periodNumber, periodStart, periodEnd,
                    amount == null ? null : Money.of(amount, Currency.of(currency.strip())), chargeId, detail, occurredAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcSubscriptionReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SubscriptionView> list(UUID customerId, UUID walletId, SubscriptionStatus status, UUID before, int limit, List<UUID> customerIds) {
        if (customerIds != null && customerIds.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from subscriptions where true");
        if (customerId != null) {
            sql.append(" and customer_id = ?");
            params.add(customerId);
        }
        if (walletId != null) {
            sql.append(" and wallet_id = ?");
            params.add(walletId);
        }
        if (customerIds != null) {
            sql.append(" and customer_id = any(?)");
            params.add(customerIds.toArray(UUID[]::new));
        }
        if (status != null) {
            sql.append(" and status = ?");
            params.add(status.name());
        }
        if (before != null) {
            sql.append(" and id < ?");
            params.add(before);
        }
        sql.append(" order by id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(SubscriptionRowView.class).list().stream().map(SubscriptionRowView::toView).toList();
    }

    @Override
    public Optional<SubscriptionView> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from subscriptions where id = ?").param(id).query(SubscriptionRowView.class).optional()
                .map(SubscriptionRowView::toView);
    }

    @Override
    public List<SubscriptionEventView> history(UUID subscriptionId) {
        return jdbc.sql("select e.id, e.type, e.period_number, e.period_start, e.period_end, e.amount, s.currency, e.charge_id, e.detail, e.occurred_at "
                        + "from subscription_events e join subscriptions s on s.id = e.subscription_id and s.tenant_id = e.tenant_id "
                        + "where e.subscription_id = ? order by e.occurred_at, e.id")
                .param(subscriptionId).query(EventRowView.class).list().stream().map(EventRowView::toView).toList();
    }
}
