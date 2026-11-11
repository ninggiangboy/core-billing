package dev.ngb.core_billing.payment.application.payment.port;

import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Optional;

/** The provider customer created for a Billing Customer on one Stripe configuration, so payment methods can be managed. */
public interface StripeCustomerRepository {

    Optional<String> find(CustomerId customerId, StripeConfigurationId configurationId);

    /** Inserts unless the same customer already has a provider customer on this configuration. */
    void addIfAbsent(
            TenantId tenantId, CustomerId customerId, StripeConfigurationId configurationId, String stripeCustomerId, Instant now);
}
