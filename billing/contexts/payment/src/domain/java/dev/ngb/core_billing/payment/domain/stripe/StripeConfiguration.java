package dev.ngb.core_billing.payment.domain.stripe;

import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * How one Tenant takes payments through Stripe. Secrets are held only as ciphertext and nothing here returns
 * them; the read side exposes {@link #hasWebhookSecret()} instead of the value.
 */
public final class StripeConfiguration {

    private final StripeConfigurationId id;
    private final TenantId tenantId;
    private final IntegrationMode mode;
    private final String accountId;
    private final StripeEnvironment environment;
    private ConnectionStatus status;
    private final String secretKeyCiphertext;
    private String webhookSecretCiphertext;
    private final String publishableKey;
    private boolean cardPaymentsActive;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant connectedAt;
    private Instant lastVerifiedAt;
    private Instant disconnectedAt;

    private StripeConfiguration(
            StripeConfigurationId id, TenantId tenantId, IntegrationMode mode, String accountId, StripeEnvironment environment,
            ConnectionStatus status, String secretKeyCiphertext, String webhookSecretCiphertext, String publishableKey, boolean cardPaymentsActive,
            Instant createdAt, Instant updatedAt, Instant connectedAt, Instant lastVerifiedAt, Instant disconnectedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.status = Objects.requireNonNull(status, "status");
        this.secretKeyCiphertext = secretKeyCiphertext;
        this.webhookSecretCiphertext = webhookSecretCiphertext;
        this.publishableKey = publishableKey;
        this.cardPaymentsActive = cardPaymentsActive;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.connectedAt = connectedAt;
        this.lastVerifiedAt = lastVerifiedAt;
        this.disconnectedAt = disconnectedAt;
        if (mode == IntegrationMode.DIRECT && secretKeyCiphertext == null) {
            throw new IllegalArgumentException("a Direct configuration needs a secret key");
        }
        if (mode == IntegrationMode.CONNECT && (secretKeyCiphertext != null || webhookSecretCiphertext != null)) {
            throw new IllegalArgumentException("a Connect configuration holds no secrets of the Tenant");
        }
    }

    /** The key has been checked against Stripe. It stays PENDING until the webhook secret is set. */
    public static StripeConfiguration direct(
            StripeConfigurationId id, TenantId tenantId, String accountId, StripeEnvironment environment, String secretKeyCiphertext,
            String webhookSecretCiphertext, String publishableKey, Instant now) {
        StripeConfiguration configuration = new StripeConfiguration(id, tenantId, IntegrationMode.DIRECT, accountId, environment,
                ConnectionStatus.PENDING, secretKeyCiphertext, webhookSecretCiphertext, publishableKey, true, now, now, null, now, null);
        configuration.settle(now);
        return configuration;
    }

    /** A connected account has been created and the Tenant still has to finish Stripe's onboarding. */
    public static StripeConfiguration connect(
            StripeConfigurationId id, TenantId tenantId, String accountId, StripeEnvironment environment, Instant now) {
        return new StripeConfiguration(id, tenantId, IntegrationMode.CONNECT, accountId, environment, ConnectionStatus.PENDING, null, null, null,
                false, now, now, null, null, null);
    }

    public static StripeConfiguration restore(
            StripeConfigurationId id, TenantId tenantId, IntegrationMode mode, String accountId, StripeEnvironment environment,
            ConnectionStatus status, String secretKeyCiphertext, String webhookSecretCiphertext, String publishableKey, boolean cardPaymentsActive,
            Instant createdAt, Instant updatedAt, Instant connectedAt, Instant lastVerifiedAt, Instant disconnectedAt) {
        return new StripeConfiguration(id, tenantId, mode, accountId, environment, status, secretKeyCiphertext, webhookSecretCiphertext,
                publishableKey, cardPaymentsActive, createdAt, updatedAt, connectedAt, lastVerifiedAt, disconnectedAt);
    }

    public void setWebhookSecret(String ciphertext, Instant now) {
        requireLive();
        if (mode != IntegrationMode.DIRECT) {
            throw new IllegalStateException("only a Direct configuration has a webhook secret of its own");
        }
        this.webhookSecretCiphertext = Objects.requireNonNull(ciphertext, "ciphertext");
        settle(now);
    }

    /** Records what Stripe says about the account, then recomputes whether payments may be taken. */
    public void observeAccount(boolean cardPayments, Instant now) {
        requireLive();
        this.cardPaymentsActive = cardPayments;
        this.lastVerifiedAt = now;
        settle(now);
    }

    public void disconnect(Instant now) {
        if (status == ConnectionStatus.DISCONNECTED) {
            return;
        }
        this.status = ConnectionStatus.DISCONNECTED;
        this.disconnectedAt = now;
        this.updatedAt = now;
    }

    private void settle(Instant now) {
        if (mode == IntegrationMode.DIRECT) {
            this.status = webhookSecretCiphertext == null ? ConnectionStatus.PENDING : ConnectionStatus.ACTIVE;
        } else if (cardPaymentsActive) {
            this.status = ConnectionStatus.ACTIVE;
        } else {
            // Never active yet means onboarding is unfinished; having been active means Stripe restricted it since.
            this.status = connectedAt == null ? ConnectionStatus.PENDING : ConnectionStatus.RESTRICTED;
        }
        if (status == ConnectionStatus.ACTIVE && connectedAt == null) {
            this.connectedAt = now;
        }
        this.updatedAt = now;
    }

    private void requireLive() {
        if (status == ConnectionStatus.DISCONNECTED) {
            throw new IllegalStateException("the configuration is disconnected");
        }
    }

    /** True when a payment can be created now. */
    public boolean canTakePayments() {
        return status == ConnectionStatus.ACTIVE;
    }

    public boolean hasWebhookSecret() {
        return webhookSecretCiphertext != null;
    }

    public StripeConfigurationId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public IntegrationMode mode() {
        return mode;
    }

    public String accountId() {
        return accountId;
    }

    public StripeEnvironment environment() {
        return environment;
    }

    public ConnectionStatus status() {
        return status;
    }

    public String secretKeyCiphertext() {
        return secretKeyCiphertext;
    }

    public String webhookSecretCiphertext() {
        return webhookSecretCiphertext;
    }

    public String publishableKey() {
        return publishableKey;
    }

    public boolean cardPaymentsActive() {
        return cardPaymentsActive;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant connectedAt() {
        return connectedAt;
    }

    public Instant lastVerifiedAt() {
        return lastVerifiedAt;
    }

    public Instant disconnectedAt() {
        return disconnectedAt;
    }
}
