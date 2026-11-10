package io.corebilling.payment.adapter.persistence.stripe;

import io.corebilling.payment.domain.stripe.ConnectionStatus;
import io.corebilling.payment.domain.stripe.IntegrationMode;
import io.corebilling.payment.domain.stripe.StripeConfiguration;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.payment.domain.stripe.StripeEnvironment;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("stripe_configurations")
record StripeConfigurationRow(
        @Id UUID id,
        UUID tenantId,
        String mode,
        String accountId,
        String environment,
        String status,
        String secretKeyCiphertext,
        String webhookSecretCiphertext,
        String publishableKey,
        boolean cardPaymentsActive,
        Instant createdAt,
        Instant updatedAt,
        Instant connectedAt,
        Instant lastVerifiedAt,
        Instant disconnectedAt) {

    static StripeConfigurationRow from(StripeConfiguration configuration) {
        return new StripeConfigurationRow(
                configuration.id().value(),
                configuration.tenantId().value(),
                configuration.mode().name(),
                configuration.accountId(),
                configuration.environment().name(),
                configuration.status().name(),
                configuration.secretKeyCiphertext(),
                configuration.webhookSecretCiphertext(),
                configuration.publishableKey(),
                configuration.cardPaymentsActive(),
                configuration.createdAt(),
                configuration.updatedAt(),
                configuration.connectedAt(),
                configuration.lastVerifiedAt(),
                configuration.disconnectedAt());
    }

    StripeConfiguration toDomain() {
        return StripeConfiguration.restore(
                new StripeConfigurationId(id),
                new TenantId(tenantId),
                IntegrationMode.valueOf(mode),
                accountId,
                StripeEnvironment.valueOf(environment),
                ConnectionStatus.valueOf(status),
                secretKeyCiphertext,
                webhookSecretCiphertext,
                publishableKey,
                cardPaymentsActive,
                createdAt,
                updatedAt,
                connectedAt,
                lastVerifiedAt,
                disconnectedAt);
    }
}
