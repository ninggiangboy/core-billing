package dev.ngb.core_billing.payment.application.stripe.query;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.platform.api.error.NotFoundException;

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
