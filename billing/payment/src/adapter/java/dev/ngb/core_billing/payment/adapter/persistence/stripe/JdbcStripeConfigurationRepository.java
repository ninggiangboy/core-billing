package dev.ngb.core_billing.payment.adapter.persistence.stripe;

import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcStripeConfigurationRepository implements StripeConfigurationRepository {

    private static final String COLUMNS = "id, tenant_id, mode, account_id, environment, status, secret_key_ciphertext, webhook_secret_ciphertext, "
            + "publishable_key, card_payments_active, created_at, updated_at, connected_at, last_verified_at, disconnected_at";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcStripeConfigurationRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void add(StripeConfiguration c) {
        aggregates.insert(StripeConfigurationRow.from(c));
    }

    @Override
    public void update(StripeConfiguration c) {
        aggregates.update(StripeConfigurationRow.from(c));
    }

    @Override
    public Optional<StripeConfiguration> findById(StripeConfigurationId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), StripeConfigurationRow.class)).map(StripeConfigurationRow::toDomain);
    }

    @Override
    public Optional<StripeConfiguration> findLive() {
        return jdbc.sql("select " + COLUMNS + " from stripe_configurations where status <> 'DISCONNECTED'")
                .query(StripeConfigurationRow.class)
                .optional()
                .map(StripeConfigurationRow::toDomain);
    }
}
