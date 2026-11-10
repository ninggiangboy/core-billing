package io.corebilling.payment.application;

import io.corebilling.payment.domain.EventStatus;
import io.corebilling.payment.domain.IncidentKind;
import io.corebilling.payment.domain.StoredEventId;
import io.corebilling.payment.domain.StripeConfiguration;
import io.corebilling.payment.domain.StripeConfigurationId;
import io.corebilling.payment.domain.StripeEnvironment;
import io.corebilling.payment.domain.StripeEvent;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.Optional;

/**
 * Accepts a Stripe webhook: verifies the signature, works out which tenant and Stripe account it is for, stores
 * the event once, and processes it. Nothing is read from the body until the signature has been checked, and the
 * tenant comes from Billing's own routing table, never from the request.
 *
 * <p>Once an event is verified and stored the answer is always "received": from then on retries are Billing's
 * job, and answering with an error would only make Stripe repeat what is already stored.
 */
public class ReceiveStripeWebhookUseCase {

    public enum Disposition { PROCESSED, IGNORED, DEFERRED, DEAD, DUPLICATE }

    public record Receipt(Disposition disposition, String detail) {}

    private final UnitOfWork unitOfWork;
    private final TenantContext tenantContext;
    private final StripeRoutes routes;
    private final StripeConfigurationRepository configurations;
    private final StripeEventRepository events;
    private final IncidentRepository incidents;
    private final PaymentProvider provider;
    private final StripeEventProcessor processor;
    private final SecretCipher cipher;
    private final PaymentSettings settings;
    private final IdGenerator ids;
    private final Clock clock;

    public ReceiveStripeWebhookUseCase(
            UnitOfWork unitOfWork, TenantContext tenantContext, StripeRoutes routes, StripeConfigurationRepository configurations,
            StripeEventRepository events, IncidentRepository incidents, PaymentProvider provider, StripeEventProcessor processor,
            SecretCipher cipher, PaymentSettings settings, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.tenantContext = tenantContext;
        this.routes = routes;
        this.configurations = configurations;
        this.events = events;
        this.incidents = incidents;
        this.provider = provider;
        this.processor = processor;
        this.cipher = cipher;
        this.settings = settings;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * A Direct webhook: the URL names the configuration, whose own webhook secret verifies the body.
     *
     * @throws NotFoundException when the configuration is unknown or disconnected
     * @throws InvalidSignatureException when the body does not verify
     */
    public Receipt receiveDirect(StripeConfigurationId configurationId, String payload, String signature) {
        StripeRoutes.Route route = routes.byConfiguration(configurationId).orElseThrow(() -> new NotFoundException("unknown webhook endpoint"));
        return tenantContext.callAs(RequestScope.tenant(route.tenantId()), () -> {
            // A disconnected configuration still answers: payments and refunds it started may yet be settled by Stripe.
            StripeConfiguration configuration = unitOfWork.transactional(() -> configurations.findById(configurationId))
                    .orElseThrow(() -> new NotFoundException("unknown webhook endpoint"));
            if (!configuration.hasWebhookSecret()) {
                incident(configuration.tenantId(), configuration.id(), IncidentKind.SIGNATURE_REJECTED, "configuration/" + configuration.id(),
                        "A webhook arrived but no webhook secret is set, so it could not be verified");
                throw new InvalidSignatureException("no webhook secret is set for this endpoint");
            }
            PaymentProvider.ProviderEvent event = verified(configuration, payload, signature,
                    cipher.decrypt(configuration.webhookSecretCiphertext(), configuration.id().toString()));
            return store(configuration, event, payload);
        });
    }

    /**
     * The Platform's Connect webhook: one secret for all connected accounts, and the event names the account,
     * which Billing maps to a tenant.
     */
    public Receipt receiveConnect(String payload, String signature) {
        PaymentSettings.Connect connect = settings.connectSettings().orElseThrow(() -> new NotFoundException("unknown webhook endpoint"));
        PaymentProvider.ProviderEvent event;
        try {
            event = provider.verifyWebhook(payload, signature, connect.webhookSecret());
        } catch (InvalidSignatureException rejected) {
            incident(RequestScope.PLATFORM_TENANT_ID, null, IncidentKind.SIGNATURE_REJECTED, "connect", "A Connect webhook failed signature verification: "
                    + rejected.getMessage());
            throw rejected;
        }
        if (event.account() == null) {
            return new Receipt(Disposition.IGNORED, "not an event of a connected account");
        }
        Optional<StripeRoutes.Route> route = routes.byAccount(event.account());
        if (route.isEmpty()) {
            incident(RequestScope.PLATFORM_TENANT_ID, null, IncidentKind.UNKNOWN_ACCOUNT, "account/" + event.account(),
                    "A verified " + event.type() + " event arrived for an account no tenant has linked");
            return new Receipt(Disposition.IGNORED, "unknown account");
        }
        return tenantContext.callAs(RequestScope.tenant(route.get().tenantId()), () -> {
            StripeConfiguration configuration = unitOfWork.transactional(() -> configurations.findById(route.get().configurationId())).orElse(null);
            if (configuration == null) {
                return new Receipt(Disposition.IGNORED, "the account's configuration is gone");
            }
            return store(configuration, event, payload);
        });
    }

    private PaymentProvider.ProviderEvent verified(StripeConfiguration configuration, String payload, String signature, String secret) {
        try {
            return provider.verifyWebhook(payload, signature, secret);
        } catch (InvalidSignatureException rejected) {
            incident(configuration.tenantId(), configuration.id(), IncidentKind.SIGNATURE_REJECTED, "configuration/" + configuration.id(),
                    "A webhook failed signature verification: " + rejected.getMessage());
            throw rejected;
        }
    }

    private Receipt store(StripeConfiguration configuration, PaymentProvider.ProviderEvent event, String payload) {
        boolean live = event.livemode();
        if (live != (configuration.environment() == StripeEnvironment.LIVE)) {
            incident(configuration.tenantId(), configuration.id(), IncidentKind.EVENT_REJECTED, "environment-mismatch",
                    "A " + (live ? "live" : "test") + " mode event arrived for a " + configuration.environment() + " configuration (latest: " + event.id() + ")");
            return new Receipt(Disposition.IGNORED, "environment mismatch");
        }
        if (event.account() != null && !event.account().equals(configuration.accountId())) {
            incident(configuration.tenantId(), configuration.id(), IncidentKind.EVENT_REJECTED, "account-mismatch",
                    "An event for account " + event.account() + " arrived for configuration account " + configuration.accountId() + " (latest: " + event.id() + ")");
            return new Receipt(Disposition.IGNORED, "account mismatch");
        }
        StoredEventId id = new StoredEventId(ids.newId());
        boolean fresh = unitOfWork.transactional(() -> events.insertIfAbsent(StripeEvent.received(id, configuration.tenantId(), configuration.id(),
                configuration.accountId(), event.id(), event.type(), event.livemode(), payload, clock.now())));
        if (!fresh) {
            return new Receipt(Disposition.DUPLICATE, "already received");
        }
        StripeEventProcessor.Outcome outcome = processor.attempt(id);
        return new Receipt(disposition(outcome.status()), outcome.detail());
    }

    private static Disposition disposition(EventStatus status) {
        return switch (status) {
            case PROCESSED -> Disposition.PROCESSED;
            case IGNORED -> Disposition.IGNORED;
            case DEAD -> Disposition.DEAD;
            case FAILED, RECEIVED -> Disposition.DEFERRED;
        };
    }

    private void incident(TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail) {
        RequestScope scope = tenantId.equals(RequestScope.PLATFORM_TENANT_ID) ? RequestScope.platform() : RequestScope.tenant(tenantId);
        tenantContext.runAs(scope, () -> unitOfWork.transactional(
                () -> incidents.record(ids.newId(), tenantId, configurationId, kind, subject, detail, clock.now())));
    }
}
