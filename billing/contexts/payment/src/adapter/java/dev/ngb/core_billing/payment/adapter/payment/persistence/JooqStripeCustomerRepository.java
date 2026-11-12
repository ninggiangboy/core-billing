package dev.ngb.core_billing.payment.adapter.payment.persistence;

import static dev.ngb.core_billing_jooq.Tables.STRIPE_CUSTOMERS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.payment.application.payment.port.StripeCustomerRepository;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.time.Instant;
import java.util.Optional;
import org.jooq.DSLContext;

/** Stores the provider customer created for a Billing Customer on one Stripe configuration. */
public class JooqStripeCustomerRepository implements StripeCustomerRepository {

    private final DSLContext dsl;

    public JooqStripeCustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<String> find(CustomerId customerId, StripeConfigurationId configurationId) {
        return dsl.select(STRIPE_CUSTOMERS.STRIPE_CUSTOMER_ID)
                .from(STRIPE_CUSTOMERS)
                .where(STRIPE_CUSTOMERS.CUSTOMER_ID.eq(customerId.value()))
                .and(STRIPE_CUSTOMERS.CONFIGURATION_ID.eq(configurationId.value()))
                .fetchOptional(STRIPE_CUSTOMERS.STRIPE_CUSTOMER_ID);
    }

    @Override
    public void addIfAbsent(
            TenantId tenantId, CustomerId customerId, StripeConfigurationId configurationId, String stripeCustomerId, Instant now) {
        dsl.insertInto(STRIPE_CUSTOMERS)
                .set(STRIPE_CUSTOMERS.TENANT_ID, tenantId.value())
                .set(STRIPE_CUSTOMERS.CUSTOMER_ID, customerId.value())
                .set(STRIPE_CUSTOMERS.CONFIGURATION_ID, configurationId.value())
                .set(STRIPE_CUSTOMERS.STRIPE_CUSTOMER_ID, stripeCustomerId)
                .set(STRIPE_CUSTOMERS.CREATED_AT, now)
                .onConflict(STRIPE_CUSTOMERS.TENANT_ID, STRIPE_CUSTOMERS.CUSTOMER_ID, STRIPE_CUSTOMERS.CONFIGURATION_ID)
                .doNothing()
                .execute();
    }
}
