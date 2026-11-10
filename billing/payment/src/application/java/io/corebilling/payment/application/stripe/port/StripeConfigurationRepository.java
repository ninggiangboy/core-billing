package io.corebilling.payment.application.stripe.port;

import io.corebilling.payment.domain.stripe.StripeConfiguration;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import java.util.List;
import java.util.Optional;

/** Stripe configurations of the current tenant. */
public interface StripeConfigurationRepository {

    void add(StripeConfiguration configuration);

    void update(StripeConfiguration configuration);

    Optional<StripeConfiguration> findById(StripeConfigurationId id);

    /** The configuration that is not disconnected, if any. */
    Optional<StripeConfiguration> findLive();

    List<StripeConfiguration> list();
}
