package dev.ngb.billing.payment.application.payment.port;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import java.time.Instant;
import java.util.Optional;

/** The provider customer created for a Billing Customer on one Stripe configuration, so payment methods can be managed. */
public interface StripeCustomerRepository {

    Optional<String> find(CustomerId customerId, StripeConfigurationId configurationId);

    /** Inserts unless the same customer already has a provider customer on this configuration. */
    void addIfAbsent(
            TenantId tenantId, CustomerId customerId, StripeConfigurationId configurationId, String stripeCustomerId, Instant now);
}
