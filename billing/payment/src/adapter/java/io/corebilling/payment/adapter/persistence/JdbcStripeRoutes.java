package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.stripe.port.StripeRoutes;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.TenantId;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The platform-owned routing table: no row-level security, because the tenant is what is being looked up. */
public class JdbcStripeRoutes implements StripeRoutes {

    private static final RowMapper<Route> MAPPER = (rs, n) -> new Route(
            new StripeConfigurationId(Rows.uuid(rs, "configuration_id")), new TenantId(Rows.uuid(rs, "tenant_id")), rs.getString("stripe_account_id"));

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcStripeRoutes(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void register(Route route) {
        jdbc.sql("insert into stripe_routes (configuration_id, tenant_id, stripe_account_id, created_at) values (?, ?, ?, ?)")
                .params(route.configurationId().value(), route.tenantId().value(), route.stripeAccountId(), Timestamp.from(clock.now()))
                .update();
    }

    @Override
    public void remove(StripeConfigurationId configurationId) {
        jdbc.sql("delete from stripe_routes where configuration_id = ?").param(configurationId.value()).update();
    }

    @Override
    public Optional<Route> byConfiguration(StripeConfigurationId configurationId) {
        return jdbc.sql("select configuration_id, tenant_id, stripe_account_id from stripe_routes where configuration_id = ?")
                .param(configurationId.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Route> byAccount(String stripeAccountId) {
        return jdbc.sql("select configuration_id, tenant_id, stripe_account_id from stripe_routes where stripe_account_id = ?")
                .param(stripeAccountId).query(MAPPER).optional();
    }
}
