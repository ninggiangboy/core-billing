package io.corebilling.payment.application.stripe.query;

import java.util.Optional;

/** Read-only projection of the tenant's live Stripe configuration. */
public interface StripeConfigurationReadRepository {

    Optional<StripeConfigurationView> findLive();
}
