package dev.ngb.core_billing.payment.adapter.persistence.stripe;

import dev.ngb.core_billing.payment.application.stripe.port.StripeRoutes;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.sharedkernel.TenantId;
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
