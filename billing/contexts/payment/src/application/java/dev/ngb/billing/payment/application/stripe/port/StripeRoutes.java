package dev.ngb.billing.payment.application.stripe.port;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.payment.domain.stripe.StripeConfigurationId;
import java.util.Optional;

/**
 * Which tenant a Stripe configuration or account belongs to. A webhook arrives before any tenant scope exists,
 * so this lookup is platform-owned and holds no secrets.
 */
public interface StripeRoutes {

    record Route(StripeConfigurationId configurationId, TenantId tenantId, String stripeAccountId) {}

    void register(Route route);

    void remove(StripeConfigurationId configurationId);

    Optional<Route> byConfiguration(StripeConfigurationId configurationId);

    Optional<Route> byAccount(String stripeAccountId);
}
