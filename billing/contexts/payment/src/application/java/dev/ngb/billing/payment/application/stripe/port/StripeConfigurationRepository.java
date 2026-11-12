package dev.ngb.billing.payment.application.stripe.port;

import dev.ngb.billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.billing.payment.domain.stripe.StripeConfigurationId;
import java.util.Optional;

/** Stripe configurations of the current tenant. */
public interface StripeConfigurationRepository {

    void add(StripeConfiguration configuration);

    void update(StripeConfiguration configuration);

    Optional<StripeConfiguration> findById(StripeConfigurationId id);

    /** The configuration that is not disconnected, if any. */
    Optional<StripeConfiguration> findLive();

}
