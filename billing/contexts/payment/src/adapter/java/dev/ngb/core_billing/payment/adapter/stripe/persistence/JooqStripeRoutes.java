package dev.ngb.core_billing.payment.adapter.stripe.persistence;

import static dev.ngb.core_billing_jooq.Tables.STRIPE_ROUTES;

import dev.ngb.core_billing.payment.application.stripe.port.StripeRoutes;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing_jooq.tables.records.StripeRoutesRecord;
import java.util.Optional;
import org.jooq.DSLContext;

/** The platform-owned routing table: no row-level security, because the tenant is what is being looked up. */
public class JooqStripeRoutes implements StripeRoutes {

    private final DSLContext dsl;
    private final Clock clock;

    public JooqStripeRoutes(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public void register(Route route) {
        dsl.insertInto(STRIPE_ROUTES)
                .set(STRIPE_ROUTES.CONFIGURATION_ID, route.configurationId().value())
                .set(STRIPE_ROUTES.TENANT_ID, route.tenantId().value())
                .set(STRIPE_ROUTES.STRIPE_ACCOUNT_ID, route.stripeAccountId())
                .set(STRIPE_ROUTES.CREATED_AT, clock.now())
                .execute();
    }

    @Override
    public void remove(StripeConfigurationId configurationId) {
        dsl.deleteFrom(STRIPE_ROUTES).where(STRIPE_ROUTES.CONFIGURATION_ID.eq(configurationId.value())).execute();
    }

    @Override
    public Optional<Route> byConfiguration(StripeConfigurationId configurationId) {
        return dsl.selectFrom(STRIPE_ROUTES)
                .where(STRIPE_ROUTES.CONFIGURATION_ID.eq(configurationId.value()))
                .fetchOptional(JooqStripeRoutes::toRoute);
    }

    @Override
    public Optional<Route> byAccount(String stripeAccountId) {
        return dsl.selectFrom(STRIPE_ROUTES)
                .where(STRIPE_ROUTES.STRIPE_ACCOUNT_ID.eq(stripeAccountId))
                .fetchOptional(JooqStripeRoutes::toRoute);
    }

    private static Route toRoute(StripeRoutesRecord row) {
        return new Route(new StripeConfigurationId(row.getConfigurationId()), new TenantId(row.getTenantId()), row.getStripeAccountId());
    }
}
