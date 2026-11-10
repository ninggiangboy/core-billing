package io.corebilling.payment.adapter.persistence.stripe;

import io.corebilling.payment.application.stripe.port.StripeRoutes;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("stripe_routes")
record StripeRouteRow(
        @Id UUID configurationId,
        UUID tenantId,
        String stripeAccountId,
        Instant createdAt) {

    StripeRoutes.Route toRoute() {
        return new StripeRoutes.Route(new StripeConfigurationId(configurationId), new TenantId(tenantId), stripeAccountId);
    }
}
