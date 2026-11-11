package dev.ngb.core_billing.payment.adapter.persistence.stripe;

import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationReadRepository;
import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationView;
import dev.ngb.core_billing.payment.domain.stripe.ConnectionStatus;
import dev.ngb.core_billing.payment.domain.stripe.IntegrationMode;
import dev.ngb.core_billing.payment.domain.stripe.StripeEnvironment;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects the live Stripe configuration straight into the view; secrets are never selected. */
public class JdbcStripeConfigurationReadRepository implements StripeConfigurationReadRepository {

    private static final String COLUMNS = "id, mode, account_id, environment, status, publishable_key, webhook_secret_ciphertext is not null as webhook_secret_set, "
            + "card_payments_active, created_at, updated_at, connected_at, last_verified_at";

    private record Row(
            UUID id,
            String mode,
            String accountId,
            String environment,
            String status,
            String publishableKey,
            boolean webhookSecretSet,
            boolean cardPaymentsActive,
            Instant createdAt,
            Instant updatedAt,
            Instant connectedAt,
            Instant lastVerifiedAt) {

        StripeConfigurationView toView() {
            return new StripeConfigurationView(
                    id,
                    IntegrationMode.valueOf(mode),
                    accountId,
                    StripeEnvironment.valueOf(environment),
                    ConnectionStatus.valueOf(status),
                    publishableKey,
                    webhookSecretSet,
                    cardPaymentsActive,
                    createdAt,
                    updatedAt,
                    connectedAt,
                    lastVerifiedAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcStripeConfigurationReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StripeConfigurationView> findLive() {
        return jdbc.sql("""
                        select %s
                        from stripe_configurations
                        where status <> 'DISCONNECTED'
                        """.formatted(COLUMNS))
                .query(Row.class)
                .optional()
                .map(Row::toView);
    }
}
