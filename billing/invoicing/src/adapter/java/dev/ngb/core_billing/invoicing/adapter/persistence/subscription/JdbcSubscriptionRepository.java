package dev.ngb.core_billing.invoicing.adapter.persistence.subscription;

import dev.ngb.core_billing.invoicing.application.subscription.port.SubscriptionRepository;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionEvent;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.sharedkernel.Causes;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcSubscriptionRepository implements SubscriptionRepository {

    private static final String COLUMNS = "id, tenant_id, customer_id, wallet_id, currency, plan_id, plan_code, cycle, anchor, periods_charged, status, "
            + "cancel_at_period_end, canceled_at, next_renewal_at, renewal_attempts, created_at, updated_at, idempotency_key, request_hash";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcSubscriptionRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void add(Subscription subscription) {
        try {
            aggregates.insert(SubscriptionRow.from(subscription));
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("this idempotency key was already used for a subscription");
            }
            throw e;
        }
    }

    @Override
    public void update(Subscription subscription) {
        aggregates.update(SubscriptionRow.from(subscription));
    }

    @Override
    public Optional<Subscription> findById(SubscriptionId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), SubscriptionRow.class)).map(SubscriptionRow::toDomain);
    }

    @Override
    public Optional<Subscription> lockById(SubscriptionId id) {
        return jdbc.sql("select " + COLUMNS + " from subscriptions where id = ? for update").param(id.value()).query(SubscriptionRow.class).optional()
                .map(SubscriptionRow::toDomain);
    }

    @Override
    public Optional<Subscription> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from subscriptions where idempotency_key = ?").param(idempotencyKey).query(SubscriptionRow.class).optional()
                .map(SubscriptionRow::toDomain);
    }

    @Override
    public void appendEvent(SubscriptionEvent event) {
        aggregates.insert(SubscriptionEventRow.from(event));
    }
}
