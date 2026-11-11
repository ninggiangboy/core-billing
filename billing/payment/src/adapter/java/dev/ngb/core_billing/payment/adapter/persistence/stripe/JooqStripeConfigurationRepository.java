package dev.ngb.core_billing.payment.adapter.persistence.stripe;

import static dev.ngb.core_billing_jooq.Tables.STRIPE_CONFIGURATIONS;

import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.core_billing.payment.domain.stripe.ConnectionStatus;
import dev.ngb.core_billing.payment.domain.stripe.IntegrationMode;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEnvironment;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing_jooq.tables.records.StripeConfigurationsRecord;
import java.util.Optional;
import org.jooq.DSLContext;

public class JooqStripeConfigurationRepository implements StripeConfigurationRepository {

    private final DSLContext dsl;

    public JooqStripeConfigurationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(StripeConfiguration c) {
        dsl.insertInto(STRIPE_CONFIGURATIONS)
                .set(STRIPE_CONFIGURATIONS.ID, c.id().value())
                .set(STRIPE_CONFIGURATIONS.TENANT_ID, c.tenantId().value())
                .set(STRIPE_CONFIGURATIONS.MODE, c.mode().name())
                .set(STRIPE_CONFIGURATIONS.ACCOUNT_ID, c.accountId())
                .set(STRIPE_CONFIGURATIONS.ENVIRONMENT, c.environment().name())
                .set(STRIPE_CONFIGURATIONS.STATUS, c.status().name())
                .set(STRIPE_CONFIGURATIONS.SECRET_KEY_CIPHERTEXT, c.secretKeyCiphertext())
                .set(STRIPE_CONFIGURATIONS.WEBHOOK_SECRET_CIPHERTEXT, c.webhookSecretCiphertext())
                .set(STRIPE_CONFIGURATIONS.PUBLISHABLE_KEY, c.publishableKey())
                .set(STRIPE_CONFIGURATIONS.CARD_PAYMENTS_ACTIVE, c.cardPaymentsActive())
                .set(STRIPE_CONFIGURATIONS.CREATED_AT, c.createdAt())
                .set(STRIPE_CONFIGURATIONS.UPDATED_AT, c.updatedAt())
                .set(STRIPE_CONFIGURATIONS.CONNECTED_AT, c.connectedAt())
                .set(STRIPE_CONFIGURATIONS.LAST_VERIFIED_AT, c.lastVerifiedAt())
                .set(STRIPE_CONFIGURATIONS.DISCONNECTED_AT, c.disconnectedAt())
                .execute();
    }

    @Override
    public void update(StripeConfiguration c) {
        dsl.update(STRIPE_CONFIGURATIONS)
                .set(STRIPE_CONFIGURATIONS.MODE, c.mode().name())
                .set(STRIPE_CONFIGURATIONS.ACCOUNT_ID, c.accountId())
                .set(STRIPE_CONFIGURATIONS.ENVIRONMENT, c.environment().name())
                .set(STRIPE_CONFIGURATIONS.STATUS, c.status().name())
                .set(STRIPE_CONFIGURATIONS.SECRET_KEY_CIPHERTEXT, c.secretKeyCiphertext())
                .set(STRIPE_CONFIGURATIONS.WEBHOOK_SECRET_CIPHERTEXT, c.webhookSecretCiphertext())
                .set(STRIPE_CONFIGURATIONS.PUBLISHABLE_KEY, c.publishableKey())
                .set(STRIPE_CONFIGURATIONS.CARD_PAYMENTS_ACTIVE, c.cardPaymentsActive())
                .set(STRIPE_CONFIGURATIONS.UPDATED_AT, c.updatedAt())
                .set(STRIPE_CONFIGURATIONS.CONNECTED_AT, c.connectedAt())
                .set(STRIPE_CONFIGURATIONS.LAST_VERIFIED_AT, c.lastVerifiedAt())
                .set(STRIPE_CONFIGURATIONS.DISCONNECTED_AT, c.disconnectedAt())
                .where(STRIPE_CONFIGURATIONS.ID.eq(c.id().value()))
                .execute();
    }

    @Override
    public Optional<StripeConfiguration> findById(StripeConfigurationId id) {
        return dsl.selectFrom(STRIPE_CONFIGURATIONS)
                .where(STRIPE_CONFIGURATIONS.ID.eq(id.value()))
                .fetchOptional(JooqStripeConfigurationRepository::toDomain);
    }

    @Override
    public Optional<StripeConfiguration> findLive() {
        return dsl.selectFrom(STRIPE_CONFIGURATIONS)
                .where(STRIPE_CONFIGURATIONS.STATUS.ne("DISCONNECTED"))
                .fetchOptional(JooqStripeConfigurationRepository::toDomain);
    }

    private static StripeConfiguration toDomain(StripeConfigurationsRecord row) {
        return StripeConfiguration.restore(
                new StripeConfigurationId(row.getId()),
                new TenantId(row.getTenantId()),
                IntegrationMode.valueOf(row.getMode()),
                row.getAccountId(),
                StripeEnvironment.valueOf(row.getEnvironment()),
                ConnectionStatus.valueOf(row.getStatus()),
                row.getSecretKeyCiphertext(),
                row.getWebhookSecretCiphertext(),
                row.getPublishableKey(),
                row.getCardPaymentsActive(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getConnectedAt(),
                row.getLastVerifiedAt(),
                row.getDisconnectedAt());
    }
}
