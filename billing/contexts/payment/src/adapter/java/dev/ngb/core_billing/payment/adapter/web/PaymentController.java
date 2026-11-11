package dev.ngb.core_billing.payment.adapter.web;

import org.springframework.context.annotation.Profile;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.payment.application.payment.command.CreateTopUpUseCase;
import dev.ngb.core_billing.payment.application.payment.query.PaymentQueries;
import dev.ngb.core_billing.payment.application.payment.command.PaymentSettings;
import dev.ngb.core_billing.payment.application.stripe.command.ReconcileStripeUseCase;
import dev.ngb.core_billing.payment.application.refund.command.RequestRefundUseCase;
import dev.ngb.core_billing.payment.application.stripe.command.StripeConfigurationService;
import dev.ngb.core_billing.payment.application.stripe.command.StripeEventAdministration;
import dev.ngb.core_billing.payment.application.stripe.query.EventAttemptView;
import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationQueries;
import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationView;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventQueries;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventView;
import dev.ngb.core_billing.payment.application.incident.query.IncidentQueries;
import dev.ngb.core_billing.payment.application.incident.query.IncidentView;
import dev.ngb.core_billing.payment.application.payment.query.PaymentAttemptView;
import dev.ngb.core_billing.payment.application.payment.query.PaymentView;
import dev.ngb.core_billing.payment.application.refund.query.RefundQueries;
import dev.ngb.core_billing.payment.application.refund.query.RefundView;
import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing.payment.domain.stripe.IntegrationMode;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.payment.domain.refund.Refund;
import dev.ngb.core_billing.payment.domain.stripe.StoredEventId;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.MoneyDto;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * The Tenant's Stripe connection, wallet top-ups, refunds, and the history of Stripe webhook processing.
 * Nothing here returns a secret: the read side only says whether a webhook secret is set. Money-moving POSTs
 * need an {@code Idempotency-Key}. Creating a payment never changes a balance; only a verified Stripe event does.
 */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

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

    public record TopUpRequest(String walletId, long amount, String currency) {}

    public record RefundRequest(Long amount, String currency, String reason) {}

    public record PaymentResponse(
            String id, String walletId, String customerId, String status, MoneyDto amount, MoneyDto refunded, String checkoutUrl, String failureReason,
            String ledgerEntryId, Instant createdAt, Instant updatedAt, Instant completedAt) {
        static PaymentResponse of(Payment p) {
            // The hosted page is only useful while the customer can still pay.
            String url = p.status() == PaymentStatus.REQUIRES_ACTION ? p.checkoutUrl() : null;
            return new PaymentResponse(p.id().toString(), p.walletId().toString(), p.customerId().toString(), p.status().name(), MoneyDto.of(p.amount()),
                    MoneyDto.of(p.refunded()), url, p.failureReason(), p.ledgerEntryId() == null ? null : p.ledgerEntryId().toString(), p.createdAt(),
                    p.updatedAt(), p.completedAt());
        }

        static PaymentResponse ofView(PaymentView p) {
            String url = p.status() == PaymentStatus.REQUIRES_ACTION ? p.checkoutUrl() : null;
            return new PaymentResponse(p.id().toString(), p.walletId().toString(), p.customerId().toString(), p.status().name(), MoneyDto.of(p.amount()),
                    MoneyDto.of(p.refunded()), url, p.failureReason(), p.ledgerEntryId() == null ? null : p.ledgerEntryId().toString(), p.createdAt(),
                    p.updatedAt(), p.completedAt());
        }
    }

    public record AttemptResponse(String failureCode, String failureMessage, Instant occurredAt) {
        static AttemptResponse ofView(PaymentAttemptView a) {
            return new AttemptResponse(a.failureCode(), a.failureMessage(), a.occurredAt());
        }
    }

    public record RefundResponse(
            String id, String paymentId, String status, MoneyDto amount, String reason, String failureReason, String ledgerEntryId, Instant createdAt,
            Instant completedAt) {
        static RefundResponse of(Refund r) {
            return new RefundResponse(r.id().toString(), r.paymentId().toString(), r.status().name(), MoneyDto.of(r.amount()), r.reason(),
                    r.failureReason(), r.ledgerEntryId() == null ? null : r.ledgerEntryId().toString(), r.createdAt(), r.completedAt());
        }

        static RefundResponse ofView(RefundView r) {
            return new RefundResponse(r.id().toString(), r.paymentId().toString(), r.status().name(), MoneyDto.of(r.amount()), r.reason(),
                    r.failureReason(), r.ledgerEntryId() == null ? null : r.ledgerEntryId().toString(), r.createdAt(), r.completedAt());
        }
    }

    public record PaymentDetailResponse(PaymentResponse payment, List<RefundResponse> refunds, List<AttemptResponse> failedAttempts) {}

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

    public record IncidentResponse(
            String id, String kind, String subject, String detail, int occurrences, Instant createdAt, Instant lastSeenAt, Instant resolvedAt) {
        static IncidentResponse ofView(IncidentView i) {
            return new IncidentResponse(i.id().toString(), i.kind().name(), i.subject(), i.detail(), i.occurrences(), i.createdAt(), i.lastSeenAt(),
                    i.resolvedAt());
        }
    }

    public record ReconcileResponse(int checked, int corrected, int mismatches) {}

    private final StripeConfigurationService configuration;
    private final StripeConfigurationQueries configurationQueries;
    private final CreateTopUpUseCase topUp;
    private final RequestRefundUseCase refund;
    private final RefundQueries refundQueries;
    private final PaymentQueries queries;
    private final StripeEventAdministration eventAdmin;
    private final StripeEventQueries eventQueries;
    private final IncidentQueries incidentQueries;
    private final ReconcileStripeUseCase reconcile;
    private final PaymentSettings settings;
    private final CurrentActor currentActor;

    public PaymentController(
            StripeConfigurationService configuration, StripeConfigurationQueries configurationQueries, CreateTopUpUseCase topUp,
            RequestRefundUseCase refund, RefundQueries refundQueries, PaymentQueries queries, StripeEventAdministration eventAdmin,
            StripeEventQueries eventQueries, IncidentQueries incidentQueries, ReconcileStripeUseCase reconcile, PaymentSettings settings,
            CurrentActor currentActor) {
        this.configuration = configuration;
        this.configurationQueries = configurationQueries;
        this.topUp = topUp;
        this.refund = refund;
        this.refundQueries = refundQueries;
        this.queries = queries;
        this.eventAdmin = eventAdmin;
        this.eventQueries = eventQueries;
        this.incidentQueries = incidentQueries;
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

    /**
     * Starts a wallet top-up and returns the Stripe-hosted page where the customer pays. 201 for a new payment,
     * 200 when the {@code Idempotency-Key} was used before. The wallet is credited only after Stripe confirms.
     */
    @PostMapping("/payments/top-ups")
    @RequiresPermission(Permission.PAYMENT_CREATE)
    public ResponseEntity<PaymentResponse> createTopUp(
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody TopUpRequest request) {
        if (request.currency() == null || request.walletId() == null) {
            throw new IllegalArgumentException("walletId and currency are required");
        }
        CreateTopUpUseCase.Result result = topUp.execute(new CreateTopUpUseCase.Command(WalletId.of(request.walletId()),
                Money.of(request.amount(), Currency.of(request.currency())), key, Audit.of(currentActor.get(), requestId), currentActor.caller()));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(PaymentResponse.of(result.payment()));
    }

    @GetMapping("/payments")
    @RequiresPermission(Permission.PAYMENT_READ)
    public List<PaymentResponse> payments(
            @RequestParam(required = false) String walletId, @RequestParam(required = false) String status, @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit) {
        return queries.list(walletId == null ? null : WalletId.of(walletId), status == null ? null : PaymentStatus.valueOf(status),
                before == null ? null : UUID.fromString(before), limit, currentActor.caller()).stream().map(PaymentResponse::ofView).toList();
    }

    @GetMapping("/payments/{id}")
    @RequiresPermission(Permission.PAYMENT_READ)
    public PaymentDetailResponse payment(@PathVariable String id) {
        PaymentQueries.PaymentDetail detail = queries.payment(UUID.fromString(id), currentActor.caller());
        return new PaymentDetailResponse(PaymentResponse.ofView(detail.payment()), detail.refunds().stream().map(RefundResponse::ofView).toList(),
                detail.attempts().stream().map(AttemptResponse::ofView).toList());
    }

    /**
     * Refunds a payment, in full when {@code amount} is omitted. The unspent balance is held at once, Stripe is
     * asked, and the wallet is debited when Stripe confirms. 409 when the amount exceeds what is left to refund
     * or the wallet has already spent it.
     */
    @PostMapping("/payments/{id}/refunds")
    @RequiresPermission(Permission.REFUND_CREATE)
    public ResponseEntity<RefundResponse> requestRefund(
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody RefundRequest request) {
        Money amount = null;
        if (request.amount() != null) {
            if (request.currency() == null) {
                throw new IllegalArgumentException("currency is required with an amount");
            }
            amount = Money.of(request.amount(), Currency.of(request.currency()));
        }
        RequestRefundUseCase.Result result = refund.execute(new RequestRefundUseCase.Command(PaymentId.of(id), amount, request.reason(), key, Audit.of(currentActor.get(), requestId)));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(RefundResponse.of(result.refund()));
    }

    @GetMapping("/refunds/{id}")
    @RequiresPermission(Permission.PAYMENT_READ)
    public RefundResponse getRefund(@PathVariable String id) {
        return RefundResponse.ofView(refundQueries.get(UUID.fromString(id), currentActor.caller()));
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
        eventAdmin.retry(Audit.of(currentActor.get(), requestId), StoredEventId.of(id));
        return eventDetail(eventQueries.get(UUID.fromString(id)));
    }

    @GetMapping("/stripe/incidents")
    @RequiresPermission(Permission.STRIPE_READ)
    public List<IncidentResponse> incidents(@RequestParam(defaultValue = "true") boolean open, @RequestParam(defaultValue = "50") int limit) {
        return incidentQueries.list(open, limit).stream().map(IncidentResponse::ofView).toList();
    }

    @PostMapping("/stripe/incidents/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public void resolveIncident(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        eventAdmin.resolveIncident(Audit.of(currentActor.get(), requestId), UUID.fromString(id));
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
