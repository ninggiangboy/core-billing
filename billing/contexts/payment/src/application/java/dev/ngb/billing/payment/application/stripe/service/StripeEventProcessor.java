package dev.ngb.billing.payment.application.stripe.service;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core.util.Causes;
import dev.ngb.billing.payment.application.incident.port.IncidentRepository;
import dev.ngb.billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.billing.payment.application.payment.service.PaymentSettings;
import dev.ngb.billing.payment.application.payment.service.PaymentSettlement;
import dev.ngb.billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.billing.payment.application.stripe.port.StripeEventRepository;
import dev.ngb.billing.payment.domain.incident.IncidentKind;
import dev.ngb.billing.payment.domain.stripe.EventStatus;
import dev.ngb.billing.payment.domain.stripe.IntegrationMode;
import dev.ngb.billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.billing.payment.domain.stripe.StripeEvent;
import dev.ngb.billing.payment.domain.stripe.StripeEventId;
import dev.ngb.billing.platform.api.error.NotFoundException;
import java.time.Instant;
import java.util.function.DoubleSupplier;

/**
 * Acts on a stored Stripe event. One attempt is one transaction: the payment (or refund) change, the wallet
 * credit or debit, the outbox events and the event's own status commit together or not at all. If the attempt
 * throws, a second transaction records the failure and schedules the retry, so the history survives the rollback.
 */
public class StripeEventProcessor {

    /**
     * Where the event stands after a call, and whether this call was the one that tried it. A call that found the
     * event final, not due, or claimed by another worker reports {@code attempted = false}, so a caller counting
     * outcomes never counts the same attempt twice.
     */
    public record Outcome(EventStatus status, String detail, boolean attempted) {}

    /** What an event needs from Stripe, read before the transaction opens so no row lock is held across a network call. */
    private record Prefetch(PaymentProvider.Intent intent, PaymentProvider.AccountState account) {
        static final Prefetch NONE = new Prefetch(null, null);
    }

    private final UnitOfWork unitOfWork;
    private final StripeEventRepository events;
    private final StripeConfigurationRepository configurations;
    private final IncidentRepository incidents;
    private final PaymentProvider provider;
    private final ProviderAccess access;
    private final PaymentSettlement settlement;
    private final PaymentSettings settings;
    private final IdGenerator ids;
    private final Clock clock;
    private final DoubleSupplier random;

    public StripeEventProcessor(
            UnitOfWork unitOfWork, StripeEventRepository events, StripeConfigurationRepository configurations, IncidentRepository incidents,
            PaymentProvider provider, ProviderAccess access, PaymentSettlement settlement, PaymentSettings settings, IdGenerator ids, Clock clock,
            DoubleSupplier random) {
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.configurations = configurations;
        this.incidents = incidents;
        this.provider = provider;
        this.access = access;
        this.settlement = settlement;
        this.settings = settings;
        this.ids = ids;
        this.clock = clock;
        this.random = random;
    }

    /** Makes one attempt at the event and returns where it stands afterwards. Never throws for a processing failure. */
    public Outcome attempt(StripeEventId id) {
        try {
            Prefetch fetched = prefetch(id);
            return unitOfWork.transactional(() -> process(id, fetched));
        } catch (RuntimeException failure) {
            if (Causes.isRetryableContention(failure)) {
                // The lock wait timed out, so nothing was applied and nothing was attempted: the event stays due
                // and the next poll picks it up again instead of spending one of its retries.
                return currentStatus(id, "lock contention, retrying on the next poll");
            }
            return unitOfWork.transactional(() -> recordFailure(id, failure));
        }
    }

    private Outcome currentStatus(StripeEventId id, String detail) {
        return unitOfWork.transactional(() -> events.findById(id)
                .map(event -> new Outcome(event.status(), detail, false))
                .orElseThrow(() -> new NotFoundException("stripe event not found")));
    }

    /**
     * Reads from Stripe what applying the event will need: the payment intent of a completed session that is not
     * yet paid (a delayed payment method), and the state of a connected account. Everything else is in the event.
     */
    private Prefetch prefetch(StripeEventId id) {
        StripeEvent event = unitOfWork.transactional(() -> events.findById(id)).orElseThrow(() -> new NotFoundException("stripe event not found"));
        if (event.status().isFinal()) {
            return Prefetch.NONE;
        }
        PaymentProvider.ProviderEvent observed = provider.interpret(event.payload());
        PaymentProvider.Subject subject = observed.subject();
        boolean unpaidSession = subject instanceof PaymentProvider.Checkout c && c.status() == PaymentProvider.CheckoutStatus.COMPLETE
                && c.payment() == PaymentProvider.CheckoutPayment.UNPAID && c.paymentIntentId() != null && !isAsyncFailure(observed);
        if (!unpaidSession && !(subject instanceof PaymentProvider.AccountObject)) {
            return Prefetch.NONE;
        }
        StripeConfiguration configuration = unitOfWork.transactional(() -> configurations.findById(event.configurationId()))
                .orElseThrow(() -> new IllegalStateException("the event's configuration is missing"));
        if (unpaidSession) {
            return new Prefetch(provider.retrievePayment(access.of(configuration), ((PaymentProvider.Checkout) subject).paymentIntentId()), null);
        }
        if (configuration.mode() == IntegrationMode.CONNECT && configuration.accountId().equals(((PaymentProvider.AccountObject) subject).accountId())) {
            return new Prefetch(null, provider.retrieveAccount(access.platformKey(), configuration.accountId()));
        }
        return Prefetch.NONE;
    }

    private static boolean isAsyncFailure(PaymentProvider.ProviderEvent observed) {
        return "checkout.session.async_payment_failed".equals(observed.type());
    }

    private Outcome process(StripeEventId id, Prefetch fetched) {
        StripeEvent event = events.lockById(id).orElseThrow(() -> new NotFoundException("stripe event not found"));
        if (event.status().isFinal()) {
            return new Outcome(event.status(), "already " + event.status(), false);
        }
        Instant now = clock.now();
        if (event.status() == EventStatus.FAILED && event.nextAttemptAt() != null && event.nextAttemptAt().isAfter(now)) {
            // Another worker got here first and scheduled the next try; this one is not due.
            return new Outcome(event.status(), "not due until " + event.nextAttemptAt(), false);
        }
        int number = event.beginAttempt();
        StripeConfiguration configuration = configurations.findById(event.configurationId())
                .orElseThrow(() -> new IllegalStateException("the event's configuration is missing"));
        PaymentProvider.ProviderEvent observed = provider.interpret(event.payload());
        PaymentSettlement.Result result = dispatch(configuration, observed, fetched);

        switch (result.kind()) {
            case APPLIED:
                event.processed(now);
                events.addAttempt(event, number, "PROCESSED", result.detail(), now);
                break;
            case STALE:
                event.ignored(result.detail(), now);
                events.addAttempt(event, number, "IGNORED", result.detail(), now);
                break;
            case REJECTED:
                event.dead(result.detail(), now);
                events.addAttempt(event, number, "REJECTED", result.detail(), now);
                reject(event, result.detail(), now);
                break;
            case DEFERRED:
            default:
                if (number >= settings.eventMaxAttempts()) {
                    event.dead(result.detail(), now);
                    events.addAttempt(event, number, "DEFERRED", result.detail() + " (gave up)", now);
                    reject(event, result.detail(), now);
                } else {
                    event.failed(result.detail(), now.plus(settings.eventBackoff().delayAfter(number, random.getAsDouble())), now);
                    events.addAttempt(event, number, "DEFERRED", result.detail(), now);
                }
                break;
        }
        events.update(event);
        return new Outcome(event.status(), result.detail(), true);
    }

    private PaymentSettlement.Result dispatch(StripeConfiguration configuration, PaymentProvider.ProviderEvent observed, Prefetch fetched) {
        PaymentProvider.Subject subject = observed.subject();
        if (subject instanceof PaymentProvider.Checkout checkout) {
            return settlement.checkout(configuration, checkout, observed.id(), isAsyncFailure(observed), fetched.intent());
        }
        if (subject instanceof PaymentProvider.Intent intent) {
            return settlement.intent(configuration, intent, observed.id());
        }
        if (subject instanceof PaymentProvider.RefundObject refund) {
            return settlement.refund(configuration, refund, observed.id());
        }
        if (subject instanceof PaymentProvider.AccountObject account) {
            return account(configuration, account, fetched);
        }
        return new PaymentSettlement.Result(PaymentSettlement.Kind.STALE, "Billing does not act on " + observed.type());
    }

    /** A connected account changed: apply what Stripe says it can do now, read beforehand, rather than the event's own snapshot. */
    private PaymentSettlement.Result account(StripeConfiguration configuration, PaymentProvider.AccountObject account, Prefetch fetched) {
        if (fetched.account() == null || configuration.mode() != IntegrationMode.CONNECT || !configuration.accountId().equals(account.accountId())) {
            return new PaymentSettlement.Result(PaymentSettlement.Kind.STALE, "not the configured account");
        }
        if (!configuration.status().isLive()) {
            return new PaymentSettlement.Result(PaymentSettlement.Kind.STALE, "the configuration is disconnected");
        }
        configuration.observeAccount(fetched.account().cardPaymentsActive(), clock.now());
        configurations.update(configuration);
        return new PaymentSettlement.Result(PaymentSettlement.Kind.APPLIED, "account is " + configuration.status());
    }

    private Outcome recordFailure(StripeEventId id, RuntimeException failure) {
        StripeEvent event = events.lockById(id).orElseThrow(() -> new NotFoundException("stripe event not found"));
        if (event.status().isFinal()) {
            return new Outcome(event.status(), "already " + event.status(), false);
        }
        Instant now = clock.now();
        int number = event.beginAttempt();
        String why = "%s: %s".formatted(failure.getClass().getSimpleName(), failure.getMessage());
        Instant retryAt = number >= settings.eventMaxAttempts() ? null : now.plus(settings.eventBackoff().delayAfter(number, random.getAsDouble()));
        event.failed(why, retryAt, now);
        events.addAttempt(event, number, "FAILED", why, now);
        events.update(event);
        if (event.status() == EventStatus.DEAD) {
            reject(event, why, now);
        }
        return new Outcome(event.status(), why, true);
    }

    private void reject(StripeEvent event, String detail, Instant now) {
        incidents.record(ids.newId(), event.tenantId(), event.configurationId(), IncidentKind.EVENT_REJECTED, "event/" + event.eventId(),
                "%s: %s".formatted(event.type(), detail), now);
    }
}
