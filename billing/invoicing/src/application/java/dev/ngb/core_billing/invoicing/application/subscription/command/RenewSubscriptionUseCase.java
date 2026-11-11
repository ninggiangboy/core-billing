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
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Charges the next period of every due subscription, one subscription and one period per transaction.
 *
 * <p>The subscription row is locked first ({@code for update skip locked}, so two workers never renew the same one),
 * the price is read from the plan version in force when the period starts, the wallet is charged through the money
 * context, and the new schedule, the history record and the {@code subscription.renewed} notice commit together with
 * that charge. A charge key made from the subscription, the period and the attempt, and the schedule moving forward
 * in the same transaction, mean running the job again cannot charge a period twice. A period that cannot be paid is
 * recorded as a failed attempt; it never stops anything for the Tenant.
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
    private final IdGenerator ids;
    private final Clock clock;

    public RenewSubscriptionUseCase(
            UnitOfWork unitOfWork, SubscriptionRepository subscriptions, PlanPricing pricing, ChargeWallet money, EventPublisher events, RenewalSettings settings,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.subscriptions = subscriptions;
        this.pricing = pricing;
        this.money = money;
        this.events = events;
        this.settings = settings;
        this.ids = ids;
        this.clock = clock;
    }

    /** Renews what is due now, oldest first, up to one batch. */
    public Report runDue() {
        int renewed = 0;
        int ended = 0;
        int failed = 0;
        for (int i = 0; i < settings.batchSize(); i++) {
            Optional<Outcome> outcome = unitOfWork.transactional(() -> subscriptions.lockNextDue(clock.now()).map(this::process));
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
            return deferred(subscription, now);
        }
        Money amount = price.get().amount();
        UUID chargeId = null;
        if (amount.isPositive()) {
            ChargeResult charge = money.charge(new ChargeCommand(subscription.walletId(), null, amount, null, SubscriptionCharges.reference(subscription.id(), number),
                    SubscriptionCharges.idempotencyKey(subscription.id(), number, subscription.nextAttempt())));
            if (charge.status() == ChargeStatus.FAILED) {
                return deferred(subscription, now);
            }
            chargeId = charge.id().value();
        }
        subscription.recordRenewed(now);
        subscriptions.update(subscription);
        subscriptions.appendEvent(SubscriptionEvent.renewed(ids.newId(), subscription, number, amount, chargeId, now));
        events.publish(new SubscriptionNotices.Renewed(ids.newId(), subscription, number, amount, chargeId, now));
        return Outcome.RENEWED;
    }

    private Outcome deferred(Subscription subscription, Instant now) {
        subscription.deferRenewal(now.plus(settings.retryDelay()), now);
        subscriptions.update(subscription);
        return Outcome.FAILED;
    }
}
