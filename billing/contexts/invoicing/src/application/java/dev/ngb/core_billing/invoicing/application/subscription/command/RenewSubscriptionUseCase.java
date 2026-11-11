package dev.ngb.core_billing.invoicing.application.subscription.command;

import dev.ngb.core_billing.invoicing.application.subscription.port.SubscriptionRepository;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionEvent;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionNotices;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.pricing.api.plan.PeriodPrice;
import dev.ngb.core_billing.pricing.api.plan.PlanPricing;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import dev.ngb.core_billing.invoicing.application.subscription.service.RenewalSettings;
import dev.ngb.core_billing.invoicing.application.subscription.service.SubscriptionCharges;

/**
 * Charges the next period of every due subscription, one subscription and one period per transaction.
 *
 * <p>The subscription row is locked first ({@code for update skip locked}, so two workers never renew the same one),
 * the price is read from the plan version in force when the period starts, the wallet is charged through the money
 * context, and the new schedule, the history record and the {@code subscription.renewed} notice commit together with
 * that charge. A charge key made from the subscription, the period and the attempt, and the schedule moving forward
 * in the same transaction, mean running the job again cannot charge a period twice.
 *
 * <p>A period that cannot be paid (the wallet refuses the charge, or the price cannot be determined) makes the
 * subscription past due, is announced as {@code subscription.payment_failed}, and is retried on a bounded schedule
 * (see {@link RenewalSettings}). Billing never stops a Resource: the Tenant System decides what past due means. A
 * success at any attempt returns the subscription to active. A lock wait that times out is not a failed attempt:
 * nothing was charged, so the subscription stays due for the next tick.
 *
 * <p>A Prepaid wallet pays from its balance. A Postpaid wallet is charged the same way, which records the debt, and the
 * charge reaches the invoice for the period through the normal charge collection.
 */
public class RenewSubscriptionUseCase {

    public enum Outcome { RENEWED, ENDED, FAILED, NOT_DUE }

    public record Report(int renewed, int ended, int failed) {}

    private final UnitOfWork unitOfWork;
    private final SubscriptionRepository subscriptions;
    private final PlanPricing pricing;
    private final ChargeWallet money;
    private final EventPublisher events;
    private final RenewalSettings settings;
    private final DoubleSupplier jitter;
    private final IdGenerator ids;
    private final Clock clock;

    public RenewSubscriptionUseCase(
            UnitOfWork unitOfWork, SubscriptionRepository subscriptions, PlanPricing pricing, ChargeWallet money, EventPublisher events, RenewalSettings settings,
            DoubleSupplier jitter, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.subscriptions = subscriptions;
        this.pricing = pricing;
        this.money = money;
        this.events = events;
        this.settings = settings;
        this.jitter = jitter;
        this.ids = ids;
        this.clock = clock;
    }

    /** A renewal that threw instead of failing cleanly, so the subscription can be set aside and the others still renewed. */
    private static final class Poisoned extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient SubscriptionId id;

        Poisoned(SubscriptionId id, RuntimeException cause) {
            super(cause);
            this.id = id;
        }
    }

    /** Renews what is due now, oldest first, up to one batch. */
    public Report runDue() {
        int renewed = 0;
        int ended = 0;
        int failed = 0;
        Set<UUID> contended = new HashSet<>();
        for (int i = 0; i < settings.batchSize(); i++) {
            Optional<Outcome> outcome;
            try {
                outcome = unitOfWork.transactional(() -> subscriptions.lockNextDue(clock.now(), contended).map(this::guarded));
            } catch (Poisoned poisoned) {
                if (Causes.isRetryableContention(poisoned.getCause())) {
                    // A lock wait that timed out is not an attempt: nothing was charged, so the period stays due and
                    // the next tick tries again instead of counting down the retry budget. Pass over the row for the
                    // rest of this tick, or it would sit at the head of the order and starve everything behind it.
                    contended.add(poisoned.id.value());
                    continue;
                }
                // Count it as a failed attempt so one broken subscription cannot starve the rest behind it.
                unitOfWork.transactional(() -> subscriptions.lockById(poisoned.id)
                        .ifPresent(found -> failed(found, found.periodToCharge(), null, "renewal_error", clock.now())));
                failed++;
                continue;
            }
            if (outcome.isEmpty()) {
                break;
            }
            switch (outcome.get()) {
                case RENEWED -> renewed++;
                case ENDED -> ended++;
                case FAILED -> failed++;
                case NOT_DUE -> { }
                default -> throw new IllegalStateException("unexpected " + outcome.get());
            }
        }
        return new Report(renewed, ended, failed);
    }

    private Outcome guarded(Subscription subscription) {
        try {
            return process(subscription);
        } catch (RuntimeException e) {
            throw new Poisoned(subscription.id(), e);
        }
    }

    /** Renews one subscription if it is due, for example the first period of a subscription that was just created. */
    public Outcome renewIfDue(SubscriptionId id) {
        return unitOfWork.transactional(() -> process(subscriptions.lockById(id).orElseThrow(() -> new NotFoundException("subscription not found"))));
    }

    private Outcome process(Subscription subscription) {
        Instant now = clock.now();
        if (!subscription.dueAt(now)) {
            return Outcome.NOT_DUE;
        }
        if (subscription.cancelAtPeriodEnd()) {
            subscription.cancelNow(now);
            subscriptions.update(subscription);
            subscriptions.appendEvent(SubscriptionEvent.canceled(ids.newId(), subscription, "ended at the end of the paid period", now));
            return Outcome.ENDED;
        }
        int number = subscription.periodToCharge();
        Optional<PeriodPrice> price = pricing.periodPrice(subscription.planId(), subscription.periodStart(number));
        if (price.isEmpty() || price.get().cycle() != subscription.cycle() || !price.get().amount().currency().equals(subscription.currency())) {
            return failed(subscription, number, null, "price_unavailable", now);
        }
        Money amount = price.get().amount();
        UUID chargeId = null;
        if (amount.isPositive()) {
            ChargeResult charge = money.charge(new ChargeCommand(subscription.walletId(), null, amount, null, SubscriptionCharges.reference(subscription.id(), number),
                    SubscriptionCharges.idempotencyKey(subscription.id(), number, subscription.nextAttempt())));
            if (charge.status() == ChargeStatus.FAILED) {
                return failed(subscription, number, amount, charge.failureReason(), now);
            }
            chargeId = charge.id().value();
        }
        subscription.recordRenewed(now);
        subscriptions.update(subscription);
        subscriptions.appendEvent(SubscriptionEvent.renewed(ids.newId(), subscription, number, amount, chargeId, now));
        events.publish(new SubscriptionNotices.Renewed(ids.newId(), subscription, number, amount, chargeId, now));
        return Outcome.RENEWED;
    }

    /** Records an unpaid period: past due, the attempt counted, the next one scheduled until the attempts run out, and the Tenant told. */
    private Outcome failed(Subscription subscription, int number, Money amount, String reason, Instant now) {
        int attempt = subscription.nextAttempt();
        Instant retryAt = attempt >= settings.maxAttempts() ? null : now.plus(settings.backoff().delayAfter(attempt, jitter.getAsDouble()));
        subscription.recordPaymentFailed(retryAt, now);
        subscriptions.update(subscription);
        subscriptions.appendEvent(SubscriptionEvent.paymentFailed(ids.newId(), subscription, number, amount, reason, now));
        events.publish(new SubscriptionNotices.PaymentFailed(ids.newId(), subscription, number, amount, reason, attempt, retryAt, now));
        return Outcome.FAILED;
    }
}
