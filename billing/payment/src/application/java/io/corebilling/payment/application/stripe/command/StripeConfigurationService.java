package io.corebilling.payment.application.stripe.command;

import io.corebilling.payment.domain.stripe.IntegrationMode;
import io.corebilling.payment.domain.stripe.StripeConfiguration;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.Optional;
import io.corebilling.payment.application.payment.command.PaymentSettings;
import io.corebilling.payment.application.payment.port.PaymentProvider;
import io.corebilling.payment.application.shared.command.Audit;
import io.corebilling.payment.application.stripe.port.ProviderAccess;
import io.corebilling.payment.application.stripe.port.SecretCipher;
import io.corebilling.payment.application.stripe.port.StripeConfigurationRepository;
import io.corebilling.payment.application.stripe.port.StripeRoutes;
import io.corebilling.payment.domain.stripe.ConnectionStatus;

/**
 * Connects the current tenant to Stripe, in either mode, and keeps that connection's status current. The
 * provider is asked first and the database written second, so no transaction is held open across a network call.
 * Secrets go in encrypted and nothing here returns them.
 */
public class StripeConfigurationService {

    /** A configuration and, when onboarding is still due, the Stripe-hosted link to finish it. */
    public record Connection(StripeConfiguration configuration, String onboardingUrl) {}

    private final UnitOfWork unitOfWork;
    private final StripeConfigurationRepository configurations;
    private final StripeRoutes routes;
    private final PaymentProvider provider;
    private final SecretCipher cipher;
    private final ProviderAccess access;
    private final PaymentSettings settings;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public StripeConfigurationService(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, StripeRoutes routes, PaymentProvider provider, SecretCipher cipher,
            ProviderAccess access, PaymentSettings settings, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
        this.routes = routes;
        this.provider = provider;
        this.cipher = cipher;
        this.access = access;
        this.settings = settings;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Optional<StripeConfiguration> current() {
        return unitOfWork.transactional(configurations::findLive);
    }

    /** Replaces any current configuration with a Direct one built on the Tenant's own key. */
    public StripeConfiguration connectDirect(Audit audit, String secretKey, String publishableKey, String webhookSecret) {
        requireSecretKey(secretKey);
        if (publishableKey != null && !publishableKey.isBlank() && !publishableKey.startsWith("pk_")) {
            throw new IllegalArgumentException("the publishable key must start with pk_");
        }
        PaymentProvider.VerifiedAccount verified = provider.verifyKey(secretKey);
        StripeConfigurationId id = new StripeConfigurationId(ids.newId());
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            Instant now = clock.now();
            replaceCurrent(audit, now);
            claimAccount(verified.accountId(), id, tenantId);
            StripeConfiguration configuration = StripeConfiguration.direct(id, tenantId, verified.accountId(), verified.environment(),
                    cipher.encrypt(secretKey, id.toString()), blank(webhookSecret) ? null : encryptWebhookSecret(webhookSecret, id), blankToNull(publishableKey), now);
            configurations.add(configuration);
            routes.register(new StripeRoutes.Route(id, tenantId, verified.accountId()));
            record(audit, "stripe-configuration.connected-direct", id, now);
            return configuration;
        });
    }

    /** Sets (or replaces) the signing secret of the Tenant's Stripe webhook endpoint. This is what makes a Direct configuration ACTIVE. */
    public StripeConfiguration setWebhookSecret(Audit audit, String webhookSecret) {
        if (blank(webhookSecret)) {
            throw new IllegalArgumentException("webhookSecret is required");
        }
        return unitOfWork.transactional(() -> {
            StripeConfiguration configuration = live();
            if (configuration.mode() != IntegrationMode.DIRECT) {
                throw new ConflictException("only a Direct configuration has a webhook secret of its own");
            }
            Instant now = clock.now();
            configuration.setWebhookSecret(encryptWebhookSecret(webhookSecret, configuration.id()), now);
            configurations.update(configuration);
            record(audit, "stripe-configuration.webhook-secret-set", configuration.id(), now);
            return configuration;
        });
    }

    /** Starts linking a Stripe account through the Platform's Connect application. Repeating it while onboarding is open returns a fresh link. */
    public Connection startConnect(Audit audit, String displayName, String contactEmail) {
        PaymentSettings.Connect connect = settings.connectSettings().orElseThrow(() -> new ConflictException("Stripe Connect is not offered by this deployment"));
        if (blank(displayName) || displayName.length() > 200) {
            throw new IllegalArgumentException("displayName is required and limited to 200 characters");
        }
        Optional<StripeConfiguration> existing = current();
        if (existing.isPresent() && existing.get().mode() == IntegrationMode.CONNECT && existing.get().status() != io.corebilling.payment.domain.stripe.ConnectionStatus.ACTIVE) {
            return new Connection(existing.get(), provider.createOnboardingLink(connect.platformSecretKey(), existing.get().accountId(),
                    connect.onboardingReturnUrl(), connect.onboardingRefreshUrl()));
        }
        StripeConfigurationId id = new StripeConfigurationId(ids.newId());
        String accountId = provider.createConnectedAccount(connect.platformSecretKey(), displayName.strip(), blankToNull(contactEmail), "connect-" + id);
        StripeConfiguration saved = unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            Instant now = clock.now();
            replaceCurrent(audit, now);
            claimAccount(accountId, id, tenantId);
            StripeConfiguration configuration = StripeConfiguration.connect(id, tenantId, accountId, access.platformEnvironment(), now);
            configurations.add(configuration);
            routes.register(new StripeRoutes.Route(id, tenantId, accountId));
            record(audit, "stripe-configuration.connect-started", id, now);
            return configuration;
        });
        return new Connection(saved, provider.createOnboardingLink(connect.platformSecretKey(), accountId, connect.onboardingReturnUrl(),
                connect.onboardingRefreshUrl()));
    }

    /** A new onboarding link, for when the earlier one expired (Stripe's refresh path). */
    public Connection onboardingLink() {
        StripeConfiguration configuration = unitOfWork.transactional(this::live);
        if (configuration.mode() != IntegrationMode.CONNECT) {
            throw new ConflictException("only a Connect configuration has an onboarding link");
        }
        PaymentSettings.Connect connect = settings.connectSettings().orElseThrow(() -> new ConflictException("Stripe Connect is not offered by this deployment"));
        return new Connection(configuration, provider.createOnboardingLink(connect.platformSecretKey(), configuration.accountId(),
                connect.onboardingReturnUrl(), connect.onboardingRefreshUrl()));
    }

    /** Asks Stripe what the account can do now (Stripe's return path) or re-checks a Direct key. */
    public StripeConfiguration refresh(Audit audit) {
        StripeConfiguration snapshot = unitOfWork.transactional(this::live);
        boolean cardPayments;
        if (snapshot.mode() == IntegrationMode.CONNECT) {
            cardPayments = provider.retrieveAccount(access.platformKey(), snapshot.accountId()).cardPaymentsActive();
        } else {
            PaymentProvider.VerifiedAccount verified = provider.verifyKey(access.of(snapshot).secretKey());
            if (!verified.accountId().equals(snapshot.accountId())) {
                throw new ConflictException("the stored key now belongs to a different Stripe account");
            }
            cardPayments = true;
        }
        return unitOfWork.transactional(() -> {
            StripeConfiguration configuration = live();
            Instant now = clock.now();
            configuration.observeAccount(cardPayments, now);
            configurations.update(configuration);
            record(audit, "stripe-configuration.refreshed", configuration.id(), now);
            return configuration;
        });
    }

    public void disconnect(Audit audit) {
        unitOfWork.transactional(() -> {
            StripeConfiguration configuration = live();
            Instant now = clock.now();
            configuration.disconnect(now);
            configurations.update(configuration);
            // The route stays: payments and refunds already started may still be settled by Stripe, and the
            // account stays claimed by this tenant meanwhile.
            record(audit, "stripe-configuration.disconnected", configuration.id(), now);
        });
    }

    /** Disconnects the current configuration. Its route is kept, so what it started can still be settled. */
    private void replaceCurrent(Audit audit, Instant now) {
        configurations.findLive().ifPresent(old -> {
            old.disconnect(now);
            configurations.update(old);
            record(audit, "stripe-configuration.replaced", old.id(), now);
        });
    }

    /**
     * An account belongs to one tenant at a time. This tenant may link it again (for example with a rotated key),
     * which moves the route to the new configuration; the payments it already holds still belong to the account.
     * Another tenant cannot take it.
     */
    private void claimAccount(String accountId, StripeConfigurationId mine, TenantId tenantId) {
        routes.byAccount(accountId).ifPresent(route -> {
            if (!route.tenantId().equals(tenantId)) {
                throw new ConflictException("that Stripe account is already linked");
            }
            if (!route.configurationId().equals(mine)) {
                routes.remove(route.configurationId());
            }
        });
    }

    private StripeConfiguration live() {
        return configurations.findLive().orElseThrow(() -> new NotFoundException("Stripe is not configured"));
    }

    private String encryptWebhookSecret(String secret, StripeConfigurationId id) {
        if (!secret.startsWith("whsec_")) {
            throw new IllegalArgumentException("the webhook secret must start with whsec_");
        }
        return cipher.encrypt(secret.strip(), id.toString());
    }

    private static void requireSecretKey(String key) {
        if (key == null || !(key.startsWith("sk_") || key.startsWith("rk_")) || key.length() < 20 || key.length() > 300) {
            throw new IllegalArgumentException("secretKey must be a Stripe secret or restricted key (sk_... or rk_...)");
        }
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String blankToNull(String text) {
        return blank(text) ? null : text.strip();
    }

    private void record(Audit audit, String action, StripeConfigurationId id, Instant now) {
        auditLog.record(AuditEntry.tenant(tenantContext.requireTenant(), audit.actor(), action, "stripe-configuration/" + id, audit.requestId(), now));
    }
}
