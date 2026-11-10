package io.corebilling.payment.application.stripe.query;

import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.sharedkernel.UnitOfWork;

/** Reads the current Stripe configuration for the GET handler. */
public class StripeConfigurationQueries {

    private final UnitOfWork unitOfWork;
    private final StripeConfigurationReadRepository configurations;

    public StripeConfigurationQueries(UnitOfWork unitOfWork, StripeConfigurationReadRepository configurations) {
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
    }

    public StripeConfigurationView get() {
        return unitOfWork.transactional(() -> configurations.findLive().orElseThrow(() -> new NotFoundException("Stripe is not configured")));
    }
}
