package dev.ngb.core_billing.payment.adapter.stripe.web;

import dev.ngb.core_billing.payment.application.payment.service.PaymentSettings;
import dev.ngb.core_billing.payment.application.stripe.command.ManageStripeEventUseCase;
import dev.ngb.core_billing.payment.application.stripe.command.ReconcileStripeUseCase;
import dev.ngb.core_billing.payment.application.stripe.query.EventAttemptView;
import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationQueries;
import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationView;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventQueries;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventView;
import dev.ngb.core_billing.payment.application.stripe.service.StripeConfigurationService;
import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing.payment.domain.stripe.IntegrationMode;
import dev.ngb.core_billing.payment.domain.stripe.StripeEventId;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.audit.Audit;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Tenant's Stripe connection and the history of Stripe webhook processing. Nothing here returns a secret: the read side only says whether a webhook secret is set. */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class StripeController {

    /** Carries Stripe secrets, so it never prints them (a request logger would otherwise). */
    public record DirectRequest(String secretKey, String publishableKey, String webhookSecret) {
        @Override
        public String toString() {
            return "DirectRequest[redacted]";
        }
    }

    public record WebhookSecretRequest(String webhookSecret) {
        @Override
        public String toString() {
            return "WebhookSecretRequest[redacted]";
        }
    }

    public record ConnectRequest(String displayName, String contactEmail) {}

    public record ConfigurationResponse(
            String id, String mode, String accountId, String environment, String status, String publishableKey, boolean webhookSecretSet,
            boolean cardPaymentsActive, String webhookUrl, Instant createdAt, Instant updatedAt, Instant connectedAt, Instant lastVerifiedAt) {}

    /** {@code onboardingUrl} is a Stripe-hosted page; it is only present while the Tenant still has onboarding to finish. */
    public record ConnectionResponse(ConfigurationResponse configuration, String onboardingUrl) {}

    public record EventResponse(
            String id, String stripeEventId, String type, String status, int attempts, Instant nextAttemptAt, String lastError, Instant receivedAt,
            Instant processedAt) {
        static EventResponse ofView(StripeEventView e) {
            return new EventResponse(e.id().toString(), e.eventId(), e.type(), e.status().name(), e.attempts(), e.nextAttemptAt(), e.lastError(),
                    e.receivedAt(), e.processedAt());
        }
    }

    public record EventAttemptResponse(int attempt, String outcome, String detail, Instant attemptedAt) {
        static EventAttemptResponse ofView(EventAttemptView a) {
            return new EventAttemptResponse(a.attempt(), a.outcome(), a.detail(), a.attemptedAt());
        }
    }

    public record EventDetailResponse(EventResponse event, List<EventAttemptResponse> history) {}

    public record ReconcileResponse(int checked, int corrected, int mismatches) {}

    private final StripeConfigurationService configuration;
    private final StripeConfigurationQueries configurationQueries;
    private final ManageStripeEventUseCase eventAdmin;
    private final StripeEventQueries eventQueries;
    private final ReconcileStripeUseCase reconcile;
    private final PaymentSettings settings;
    private final CurrentActor currentActor;

    public StripeController(
            StripeConfigurationService configuration, StripeConfigurationQueries configurationQueries, ManageStripeEventUseCase eventAdmin,
            StripeEventQueries eventQueries, ReconcileStripeUseCase reconcile, PaymentSettings settings, CurrentActor currentActor) {
        this.configuration = configuration;
        this.configurationQueries = configurationQueries;
        this.eventAdmin = eventAdmin;
        this.eventQueries = eventQueries;
        this.reconcile = reconcile;
        this.settings = settings;
        this.currentActor = currentActor;
    }

    @PutMapping("/stripe/configuration/direct")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ConfigurationResponse connectDirect(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody DirectRequest request) {
        return configurationResponse(configuration.connectDirect(Audit.of(currentActor.get(), requestId), request.secretKey(), request.publishableKey(), request.webhookSecret()));
    }

    @PutMapping("/stripe/configuration/webhook-secret")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ConfigurationResponse setWebhookSecret(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody WebhookSecretRequest request) {
        return configurationResponse(configuration.setWebhookSecret(Audit.of(currentActor.get(), requestId), request.webhookSecret()));
    }

    @PostMapping("/stripe/configuration/connect")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ConnectionResponse startConnect(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody ConnectRequest request) {
        return connectionResponse(configuration.startConnect(Audit.of(currentActor.get(), requestId), request.displayName(), request.contactEmail()));
    }

    /** A new onboarding link, for when the earlier one has expired. */
    @PostMapping("/stripe/configuration/connect/link")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ConnectionResponse onboardingLink() {
        return connectionResponse(configuration.onboardingLink());
    }

    /** Asks Stripe what the connected account can do now. Call it when the Tenant returns from onboarding. */
    @PostMapping("/stripe/configuration/refresh")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ConfigurationResponse refresh(@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return configurationResponse(configuration.refresh(Audit.of(currentActor.get(), requestId)));
    }

    @GetMapping("/stripe/configuration")
    @RequiresPermission(Permission.STRIPE_READ)
    public ConfigurationResponse getConfiguration() {
        return configurationResponse(configurationQueries.get());
    }

    @DeleteMapping("/stripe/configuration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public void disconnect(@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        configuration.disconnect(Audit.of(currentActor.get(), requestId));
    }

    /** Newest first. Pass the last {@code id} of a page as {@code before} for the next one. */
    @GetMapping("/stripe/events")
    @RequiresPermission(Permission.STRIPE_READ)
    public List<EventResponse> events(
            @RequestParam(required = false) String status, @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return eventQueries.list(status == null ? null : EventStatus.valueOf(status),
                before == null ? null : UUID.fromString(before), limit).stream().map(EventResponse::ofView).toList();
    }

    @GetMapping("/stripe/events/{id}")
    @RequiresPermission(Permission.STRIPE_READ)
    public EventDetailResponse event(@PathVariable String id) {
        return eventDetail(eventQueries.get(UUID.fromString(id)));
    }

    /** Tries an event that failed or gave up again, now. */
    @PostMapping("/stripe/events/{id}/retry")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public EventDetailResponse retry(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        eventAdmin.retry(Audit.of(currentActor.get(), requestId), StripeEventId.of(id));
        return eventDetail(eventQueries.get(UUID.fromString(id)));
    }

    /** Compares recent payments and refunds with Stripe now, instead of waiting for the scheduled run. */
    @PostMapping("/stripe/reconcile")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ReconcileResponse reconcile() {
        ReconcileStripeUseCase.Report report = reconcile.runOnce();
        return new ReconcileResponse(report.checked(), report.corrected(), report.mismatches());
    }

    private ConfigurationResponse configurationResponse(StripeConfiguration c) {
        String webhookUrl = c.mode() == IntegrationMode.DIRECT ? settings.directWebhookUrl(c.id()) : null;
        return new ConfigurationResponse(c.id().toString(), c.mode().name(), c.accountId(), c.environment().name(), c.status().name(), c.publishableKey(),
                c.hasWebhookSecret(), c.cardPaymentsActive(), webhookUrl, c.createdAt(), c.updatedAt(), c.connectedAt(), c.lastVerifiedAt());
    }

    private ConfigurationResponse configurationResponse(StripeConfigurationView c) {
        String webhookUrl = c.mode() == IntegrationMode.DIRECT ? settings.directWebhookUrl(new StripeConfigurationId(c.id())) : null;
        return new ConfigurationResponse(c.id().toString(), c.mode().name(), c.accountId(), c.environment().name(), c.status().name(), c.publishableKey(),
                c.webhookSecretSet(), c.cardPaymentsActive(), webhookUrl, c.createdAt(), c.updatedAt(), c.connectedAt(), c.lastVerifiedAt());
    }

    private ConnectionResponse connectionResponse(StripeConfigurationService.Connection connection) {
        return new ConnectionResponse(configurationResponse(connection.configuration()), connection.onboardingUrl());
    }

    private static EventDetailResponse eventDetail(StripeEventQueries.Detail detail) {
        return new EventDetailResponse(EventResponse.ofView(detail.event()), detail.attempts().stream().map(EventAttemptResponse::ofView).toList());
    }
}
