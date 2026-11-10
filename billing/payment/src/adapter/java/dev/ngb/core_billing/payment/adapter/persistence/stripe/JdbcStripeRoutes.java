package dev.ngb.core_billing.payment.adapter.persistence.stripe;

import dev.ngb.core_billing.payment.application.stripe.port.StripeRoutes;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.sharedkernel.Clock;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The platform-owned routing table: no row-level security, because the tenant is what is being looked up. */
public class JdbcStripeRoutes implements StripeRoutes {

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcStripeRoutes(JdbcAggregateOperations aggregates, JdbcClient jdbc, Clock clock) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void register(Route route) {
        aggregates.insert(new StripeRouteRow(route.configurationId().value(), route.tenantId().value(), route.stripeAccountId(), clock.now()));
    }

    @Override
    public void remove(StripeConfigurationId configurationId) {
        aggregates.deleteById(configurationId.value(), StripeRouteRow.class);
    }

    @Override
    public Optional<Route> byConfiguration(StripeConfigurationId configurationId) {
        return Optional.ofNullable(aggregates.findById(configurationId.value(), StripeRouteRow.class)).map(StripeRouteRow::toRoute);
    }

    @Override
    public Optional<Route> byAccount(String stripeAccountId) {
        return jdbc.sql("select configuration_id, tenant_id, stripe_account_id, created_at from stripe_routes where stripe_account_id = ?")
                .param(stripeAccountId).query(StripeRouteRow.class).optional().map(StripeRouteRow::toRoute);
    }
}
