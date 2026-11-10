package io.corebilling.payment.adapter.web;

import io.corebilling.payment.application.Audit;
import io.corebilling.payment.application.CreateTopUpUseCase;
import io.corebilling.payment.application.PaymentQueries;
import io.corebilling.payment.application.PaymentSettings;
import io.corebilling.payment.application.ReconcileStripeUseCase;
import io.corebilling.payment.application.RequestRefundUseCase;
import io.corebilling.payment.application.StripeConfigurationService;
import io.corebilling.payment.application.StripeEventAdministration;
import io.corebilling.payment.domain.EventAttempt;
import io.corebilling.payment.domain.EventStatus;
import io.corebilling.payment.domain.Incident;
import io.corebilling.payment.domain.IntegrationMode;
import io.corebilling.payment.domain.Payment;
import io.corebilling.payment.domain.PaymentAttempt;
import io.corebilling.payment.domain.PaymentId;
import io.corebilling.payment.domain.PaymentStatus;
import io.corebilling.payment.domain.Refund;
import io.corebilling.payment.domain.RefundId;
import io.corebilling.payment.domain.StoredEventId;
import io.corebilling.payment.domain.StripeConfiguration;
import io.corebilling.payment.domain.StripeEvent;
import io.corebilling.platform.api.CurrentActor;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.Permission;
import io.corebilling.platform.api.RequiresPermission;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
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

    public record MoneyDto(long amount, String currency) {
        static MoneyDto of(Money money) {
            return new MoneyDto(money.minorUnits(), money.currency().code());
        }
    }

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
    }

    public record AttemptResponse(String failureCode, String failureMessage, Instant occurredAt) {
        static AttemptResponse of(PaymentAttempt a) {
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
    }

    public record PaymentDetailResponse(PaymentResponse payment, List<RefundResponse> refunds, List<AttemptResponse> failedAttempts) {}

    public record EventResponse(
            String id, String stripeEventId, String type, String status, int attempts, Instant nextAttemptAt, String lastError, Instant receivedAt,
            Instant processedAt) {
        static EventResponse of(StripeEvent e) {
            return new EventResponse(e.id().toString(), e.eventId(), e.type(), e.status().name(), e.attempts(), e.nextAttemptAt(), e.lastError(),
                    e.receivedAt(), e.processedAt());
        }
    }

    public record EventAttemptResponse(int attempt, String outcome, String detail, Instant attemptedAt) {
        static EventAttemptResponse of(EventAttempt a) {
            return new EventAttemptResponse(a.number(), a.outcome(), a.detail(), a.attemptedAt());
        }
    }

    public record EventDetailResponse(EventResponse event, List<EventAttemptResponse> history) {}

    public record IncidentResponse(
            String id, String kind, String subject, String detail, int occurrences, Instant createdAt, Instant lastSeenAt, Instant resolvedAt) {
        static IncidentResponse of(Incident i) {
            return new IncidentResponse(i.id().toString(), i.kind().name(), i.subject(), i.detail(), i.occurrences(), i.createdAt(), i.lastSeenAt(),
                    i.resolvedAt());
        }
    }

    public record ReconcileResponse(int checked, int corrected, int mismatches) {}

    private final StripeConfigurationService configuration;
    private final CreateTopUpUseCase topUp;
    private final RequestRefundUseCase refund;
    private final PaymentQueries queries;
    private final StripeEventAdministration eventAdmin;
    private final ReconcileStripeUseCase reconcile;
    private final PaymentSettings settings;
    private final CurrentActor currentActor;

    public PaymentController(
            StripeConfigurationService configuration, CreateTopUpUseCase topUp, RequestRefundUseCase refund, PaymentQueries queries,
            StripeEventAdministration eventAdmin, ReconcileStripeUseCase reconcile, PaymentSettings settings, CurrentActor currentActor) {
        this.configuration = configuration;
        this.topUp = topUp;
        this.refund = refund;
        this.queries = queries;
        this.eventAdmin = eventAdmin;
        this.reconcile = reconcile;
        this.settings = settings;
        this.currentActor = currentActor;
    }

    @PutMapping("/stripe/configuration/direct")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ConfigurationResponse connectDirect(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody DirectRequest request) {
        return configurationResponse(configuration.connectDirect(audit(requestId), request.secretKey(), request.publishableKey(), request.webhookSecret()));
    }

    @PutMapping("/stripe/configuration/webhook-secret")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ConfigurationResponse setWebhookSecret(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody WebhookSecretRequest request) {
        return configurationResponse(configuration.setWebhookSecret(audit(requestId), request.webhookSecret()));
    }

    @PostMapping("/stripe/configuration/connect")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public ConnectionResponse startConnect(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody ConnectRequest request) {
        return connectionResponse(configuration.startConnect(audit(requestId), request.displayName(), request.contactEmail()));
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
        return configurationResponse(configuration.refresh(audit(requestId)));
    }

    @GetMapping("/stripe/configuration")
    @RequiresPermission(Permission.STRIPE_READ)
    public ConfigurationResponse getConfiguration() {
        return configurationResponse(configuration.current().orElseThrow(() -> new NotFoundException("Stripe is not configured")));
    }

    @DeleteMapping("/stripe/configuration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public void disconnect(@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        configuration.disconnect(audit(requestId));
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
                Money.of(request.amount(), Currency.of(request.currency())), key, audit(requestId)));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(PaymentResponse.of(result.payment()));
    }

    @GetMapping("/payments")
    @RequiresPermission(Permission.PAYMENT_READ)
    public List<PaymentResponse> payments(
            @RequestParam(required = false) String walletId, @RequestParam(required = false) String status, @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit) {
        return queries.list(walletId == null ? null : WalletId.of(walletId), status == null ? null : PaymentStatus.valueOf(status),
                before == null ? null : PaymentId.of(before), limit).stream().map(PaymentResponse::of).toList();
    }

    @GetMapping("/payments/{id}")
    @RequiresPermission(Permission.PAYMENT_READ)
    public PaymentDetailResponse payment(@PathVariable String id) {
        PaymentQueries.PaymentDetail detail = queries.payment(PaymentId.of(id));
        return new PaymentDetailResponse(PaymentResponse.of(detail.payment()), detail.refunds().stream().map(RefundResponse::of).toList(),
                detail.attempts().stream().map(AttemptResponse::of).toList());
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
        RequestRefundUseCase.Result result = refund.execute(new RequestRefundUseCase.Command(PaymentId.of(id), amount, request.reason(), key, audit(requestId)));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(RefundResponse.of(result.refund()));
    }

    @GetMapping("/refunds/{id}")
    @RequiresPermission(Permission.PAYMENT_READ)
    public RefundResponse getRefund(@PathVariable String id) {
        return RefundResponse.of(queries.refund(RefundId.of(id)));
    }

    /** Newest first. Pass the last {@code id} of a page as {@code before} for the next one. */
    @GetMapping("/stripe/events")
    @RequiresPermission(Permission.STRIPE_READ)
    public List<EventResponse> events(
            @RequestParam(required = false) String status, @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return eventAdmin.list(status == null ? null : EventStatus.valueOf(status), before == null ? null : StoredEventId.of(before), limit).stream()
                .map(EventResponse::of).toList();
    }

    @GetMapping("/stripe/events/{id}")
    @RequiresPermission(Permission.STRIPE_READ)
    public EventDetailResponse event(@PathVariable String id) {
        return eventDetail(eventAdmin.get(StoredEventId.of(id)));
    }

    /** Tries an event that failed or gave up again, now. */
    @PostMapping("/stripe/events/{id}/retry")
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public EventDetailResponse retry(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return eventDetail(eventAdmin.retry(audit(requestId), StoredEventId.of(id)));
    }

    @GetMapping("/stripe/incidents")
    @RequiresPermission(Permission.STRIPE_READ)
    public List<IncidentResponse> incidents(@RequestParam(defaultValue = "true") boolean open, @RequestParam(defaultValue = "50") int limit) {
        return eventAdmin.incidents(open, limit).stream().map(IncidentResponse::of).toList();
    }

    @PostMapping("/stripe/incidents/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public void resolveIncident(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        eventAdmin.resolveIncident(audit(requestId), UUID.fromString(id));
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

    private ConnectionResponse connectionResponse(StripeConfigurationService.Connection connection) {
        return new ConnectionResponse(configurationResponse(connection.configuration()), connection.onboardingUrl());
    }

    private static EventDetailResponse eventDetail(StripeEventAdministration.Detail detail) {
        return new EventDetailResponse(EventResponse.of(detail.event()), detail.attempts().stream().map(EventAttemptResponse::of).toList());
    }

    private Audit audit(String requestId) {
        boolean usable = requestId != null && !requestId.isBlank() && requestId.length() <= 64 && requestId.chars().allMatch(c -> c > 32 && c < 127);
        return new Audit(currentActor.get().auditName(), usable ? requestId : UUID.randomUUID().toString());
    }
}
