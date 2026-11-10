package io.corebilling.payment.application.shared.command;

import io.corebilling.money.api.wallet.CreditCommand;
import io.corebilling.money.api.wallet.CreditWallet;
import io.corebilling.money.api.wallet.DebitCommand;
import io.corebilling.money.api.wallet.DebitWallet;
import io.corebilling.money.api.ledger.LedgerPostingResult;
import io.corebilling.money.api.reservation.ReleaseReservation;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.payment.domain.incident.IncidentKind;
import io.corebilling.payment.domain.payment.Payment;
import io.corebilling.payment.domain.payment.PaymentAttempt;
import io.corebilling.payment.domain.payment.PaymentEvents;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.refund.Refund;
import io.corebilling.payment.domain.refund.RefundId;
import io.corebilling.payment.domain.stripe.StripeConfiguration;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.corebilling.payment.application.incident.port.IncidentRepository;
import io.corebilling.payment.application.payment.port.PaymentProvider;
import io.corebilling.payment.application.payment.port.PaymentRepository;
import io.corebilling.payment.application.refund.port.RefundRepository;
import io.corebilling.payment.application.stripe.port.StripeConfigurationRepository;

/**
 * The one place where what Stripe says changes what Billing holds. A webhook event and the reconciliation job
 * both end here, so a correction found by reconciliation takes exactly the path a verified event takes.
 * It never calls the payment provider: anything it needs from Stripe is read by the caller before the
 * transaction opens and passed in, so no row lock is ever held across a network call.
 *
 * <p>Observations are states, not commands: each one is compared with the payment (or refund) and applied only
 * if it moves it forward, so a repeated, late or out-of-order observation changes nothing. Every method must run
 * inside the caller's transaction, which also carries the credit or debit of the wallet and the outbox events.
 */
public class PaymentSettlement {

    public enum Kind { APPLIED, STALE, DEFERRED, REJECTED }

    public record Result(Kind kind, String detail) {
        static Result applied(String detail) {
            return new Result(Kind.APPLIED, detail);
        }

        static Result stale(String detail) {
            return new Result(Kind.STALE, detail);
        }

        static Result deferred(String detail) {
            return new Result(Kind.DEFERRED, detail);
        }

        static Result rejected(String detail) {
            return new Result(Kind.REJECTED, detail);
        }
    }

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final IncidentRepository incidents;
    private final StripeConfigurationRepository configurations;
    private final CreditWallet credit;
    private final DebitWallet debit;
    private final ReleaseReservation release;
    private final EventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public PaymentSettlement(
            PaymentRepository payments, RefundRepository refunds, IncidentRepository incidents, StripeConfigurationRepository configurations,
            CreditWallet credit, DebitWallet debit, ReleaseReservation release, EventPublisher events, IdGenerator ids, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.incidents = incidents;
        this.configurations = configurations;
        this.credit = credit;
        this.debit = debit;
        this.release = release;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * @param asyncFailed true for the event that reports a delayed payment method failed after the session completed
     * @param evidenceId the Stripe event id (or a reconciliation marker) this observation comes from
     * @param intent the session's payment intent as Stripe holds it now, read beforehand; only needed (and only
     *     read) for a completed session that is not yet paid, a delayed payment method
     */
    public Result checkout(StripeConfiguration configuration, PaymentProvider.Checkout checkout, String evidenceId, boolean asyncFailed,
            PaymentProvider.Intent intent) {
        Optional<Payment> found = locate(checkout.paymentRef(), checkout.sessionId(), checkout.paymentIntentId());
        if (found.isEmpty()) {
            return notFound(checkout.paymentRef(), "checkout session " + checkout.sessionId());
        }
        Payment payment = found.get();
        Optional<Result> mismatch = belongsTo(configuration, payment, checkout.sessionId(), checkout.paymentIntentId());
        if (mismatch.isPresent()) {
            return mismatch.get();
        }
        Instant now = clock.now();
        if (payment.checkoutSessionId() == null && checkout.sessionId() != null) {
            payment.attachCheckout(checkout.sessionId(), payment.checkoutUrl(), now);
        }
        if (!payment.attachIntent(checkout.paymentIntentId(), now)) {
            return Result.rejected("the payment is already linked to a different payment intent");
        }
        switch (checkout.status()) {
            case OPEN:
                return Result.stale("the checkout session is still open");
            case EXPIRED:
                return closeUnpaid(payment, now);
            case COMPLETE:
            default:
                break;
        }
        if (asyncFailed) {
            return fail(payment, "the payment method could not be charged", now);
        }
        if (checkout.payment() != PaymentProvider.CheckoutPayment.UNPAID) {
            return succeed(payment, checkout.amountTotal(), checkout.currency(), now);
        }
        // Completed but not yet paid: a delayed payment method. Ask Stripe where the payment stands.
        if (intent == null) {
            return move(payment.markProcessing(now), payment);
        }
        return intent(payment, intent, evidenceId, true, now);
    }

    public Result intent(StripeConfiguration configuration, PaymentProvider.Intent intent, String evidenceId) {
        Optional<Payment> found = locate(intent.paymentRef(), null, intent.intentId());
        if (found.isEmpty()) {
            return notFound(intent.paymentRef(), "payment intent " + intent.intentId());
        }
        Payment payment = found.get();
        Optional<Result> mismatch = belongsTo(configuration, payment, null, intent.intentId());
        if (mismatch.isPresent()) {
            return mismatch.get();
        }
        return intent(payment, intent, evidenceId, false, clock.now());
    }

    public Result refund(StripeConfiguration configuration, PaymentProvider.RefundObject observed, String evidenceId) {
        Optional<Refund> known = Optional.empty();
        if (observed.refundRef() != null) {
            known = parse(observed.refundRef()).flatMap(id -> refunds.findById(new RefundId(id)));
        }
        if (known.isEmpty()) {
            known = refunds.findByStripeRefundId(observed.refundId());
        }
        if (known.isEmpty()) {
            // The Tenant may use the same Stripe account for other things; only a refund of a Billing payment is Billing's business.
            if (observed.intentId() != null && payments.lockByPaymentIntent(observed.intentId()).isPresent()) {
                incidents.record(ids.newId(), configuration.tenantId(), configuration.id(), IncidentKind.RECONCILIATION_MISMATCH,
                        "refund/" + observed.refundId(), "Stripe refunded a Billing payment outside Billing; the wallet was not changed", clock.now());
            }
            return Result.stale("refund " + observed.refundId() + " was not created by Billing");
        }
        // Lock order is payment, then refund, then wallet: the same as when a refund is requested.
        Payment payment = payments.lockById(known.get().paymentId()).orElseThrow(() -> new IllegalStateException("refund without payment"));
        Refund refund = refunds.lockById(known.get().id()).orElseThrow(() -> new IllegalStateException("refund vanished"));
        if (!sameAccount(configuration, payment)) {
            return Result.rejected("the refund belongs to another Stripe account");
        }
        if (observed.amount() != refund.amount().minorUnits() || !observed.currency().equalsIgnoreCase(refund.amount().currency().code())) {
            return Result.rejected("the refund amount at Stripe differs from the one requested");
        }
        Instant now = clock.now();
        if (refund.stripeRefundId() == null) {
            refund.attachStripeRefund(observed.refundId(), now);
            refunds.update(refund);
        } else if (!refund.stripeRefundId().equals(observed.refundId())) {
            return Result.rejected("the refund is already linked to a different Stripe refund");
        }
        if (refund.status().isFinal()) {
            return Result.stale("the refund is already " + refund.status());
        }
        switch (observed.state()) {
            case SUCCEEDED:
                releaseHold(refund);
                LedgerPostingResult posted = debit.debit(new DebitCommand(payment.walletId(), refund.amount(), "refund/" + refund.id(),
                        "Stripe refund of payment " + payment.id(), "refund:" + refund.id(), "stripe"));
                refund.succeed(posted.entryId().value(), now);
                payment.addRefunded(refund.amount(), now);
                refunds.update(refund);
                payments.update(payment);
                events.publish(new PaymentEvents.RefundSucceeded(ids.newId(), refund, payment, now));
                return Result.applied("refund succeeded; the wallet was debited");
            case FAILED:
            case CANCELED:
                releaseHold(refund);
                refund.fail(observed.failureReason() == null ? "Stripe could not refund the payment" : observed.failureReason(), now);
                refunds.update(refund);
                return Result.applied("refund failed; the held balance was released");
            case PENDING:
            default:
                return Result.stale("the refund is still pending at Stripe");
        }
    }

    /** Gives the held balance back; a hold that already expired is reported by the money context, not thrown. */
    public void releaseHold(Refund refund) {
        if (refund.reservationId() != null) {
            release.release(new ReservationId(refund.reservationId()));
        }
    }

    private Result intent(Payment payment, PaymentProvider.Intent intent, String evidenceId, boolean sessionCompleted, Instant now) {
        if (!payment.attachIntent(intent.intentId(), now)) {
            return Result.rejected("the payment is already linked to a different payment intent");
        }
        switch (intent.state()) {
            case SUCCEEDED:
                return succeed(payment, intent.amount(), intent.currency(), now);
            case PROCESSING:
                return move(payment.markProcessing(now), payment);
            case REQUIRES_PAYMENT_METHOD:
                if (sessionCompleted) {
                    return fail(payment, reason(intent), now);
                }
                if (intent.failureCode() == null && intent.failureMessage() == null) {
                    return Result.stale("the customer has not paid yet");
                }
                if (payment.status().isFinal()) {
                    return Result.stale("a failed attempt after the payment was settled");
                }
                payments.addAttempt(payment.id(), new PaymentAttempt(evidenceId, intent.failureCode(), intent.failureMessage(), now));
                payments.update(payment);
                return Result.applied("recorded a failed card attempt; the customer may try again");
            case CANCELED:
                return closeUnpaid(payment, now);
            case REQUIRES_ACTION:
            case OTHER:
            default:
                return Result.stale("the payment intent is " + intent.state());
        }
    }

    private Result succeed(Payment payment, long amount, String currency, Instant now) {
        if (amount != payment.amount().minorUnits() || currency == null || !currency.equalsIgnoreCase(payment.amount().currency().code())) {
            return Result.rejected("Stripe collected " + amount + " " + currency + " but the payment is for " + payment.amount());
        }
        Payment.Change change = payment.succeed();
        if (change == Payment.Change.STALE) {
            return Result.stale("the payment already succeeded");
        }
        if (change == Payment.Change.CONTRADICTION) {
            return Result.rejected("Stripe reports payment " + payment.id() + " as paid, but it is already " + payment.status());
        }
        // One credit per payment, whichever event or reconciliation run gets here first.
        LedgerPostingResult posted = credit.credit(new CreditCommand(payment.walletId(), payment.amount(), "payment/" + payment.id(),
                "Stripe top-up " + payment.id(), "topup:" + payment.id(), "stripe"));
        payment.recordLedgerEntry(posted.entryId().value(), now);
        payments.update(payment);
        events.publish(new PaymentEvents.PaymentSucceeded(ids.newId(), payment, now));
        return Result.applied("payment succeeded; the wallet was credited");
    }

    private Result fail(Payment payment, String reason, Instant now) {
        Payment.Change change = payment.fail(reason, now);
        return settle(change, payment, true);
    }

    /** The checkout expired or the intent was canceled: failed if the customer had tried and been declined, canceled if not. */
    private Result closeUnpaid(Payment payment, Instant now) {
        List<PaymentAttempt> tried = payments.attempts(payment.id());
        if (tried.isEmpty()) {
            return settle(payment.cancel(now), payment, false);
        }
        PaymentAttempt last = tried.get(tried.size() - 1);
        String why = last.failureMessage() != null ? last.failureMessage() : last.failureCode() != null ? last.failureCode() : "the payment was declined";
        return settle(payment.fail(why, now), payment, true);
    }

    private Result move(Payment.Change change, Payment payment) {
        return settle(change, payment, false);
    }

    private Result settle(Payment.Change change, Payment payment, boolean announceFailure) {
        switch (change) {
            case APPLIED:
                payments.update(payment);
                if (announceFailure) {
                    events.publish(new PaymentEvents.PaymentFailed(ids.newId(), payment, clock.now()));
                }
                return Result.applied("payment is now " + payment.status());
            case CONTRADICTION:
                return Result.rejected("Stripe's result contradicts payment " + payment.id() + ", which is already " + payment.status());
            case STALE:
            default:
                return Result.stale("payment " + payment.id() + " is already " + payment.status() + " or further");
        }
    }

    private Optional<Result> belongsTo(StripeConfiguration configuration, Payment payment, String sessionId, String intentId) {
        if (!sameAccount(configuration, payment)) {
            return Optional.of(Result.rejected("the payment belongs to another Stripe account"));
        }
        if (sessionId != null && payment.checkoutSessionId() != null && !payment.checkoutSessionId().equals(sessionId)) {
            return Optional.of(Result.rejected("the payment is linked to a different checkout session"));
        }
        if (intentId != null && payment.paymentIntentId() != null && !payment.paymentIntentId().equals(intentId)) {
            return Optional.of(Result.rejected("the payment is linked to a different payment intent"));
        }
        return Optional.empty();
    }

    /**
     * A payment belongs to a Stripe account, not to one configuration row: replacing the key of the same account
     * must not orphan what was started before. A payment of another account is never touched.
     */
    private boolean sameAccount(StripeConfiguration configuration, Payment payment) {
        if (payment.configurationId().equals(configuration.id())) {
            return true;
        }
        return configurations.findById(payment.configurationId()).map(own -> own.accountId().equals(configuration.accountId())).orElse(false);
    }

    /**
     * Nothing local for a Stripe object. With our own reference on it, the payment may simply not be stored yet,
     * so wait and retry. Without one it was never ours: the Tenant may use the account for other things too.
     */
    private Result notFound(String ref, String what) {
        return ref != null ? Result.deferred("no payment for " + what) : Result.stale(what + " is not a Billing payment");
    }

    /** Finds and locks the payment by our own reference first, then by the Stripe objects it was linked to. */
    private Optional<Payment> locate(String paymentRef, String sessionId, String intentId) {
        Optional<Payment> found = Optional.empty();
        if (paymentRef != null) {
            found = parse(paymentRef).flatMap(id -> payments.lockById(new PaymentId(id)));
        }
        if (found.isEmpty() && sessionId != null) {
            found = payments.lockByCheckoutSession(sessionId);
        }
        if (found.isEmpty() && intentId != null) {
            found = payments.lockByPaymentIntent(intentId);
        }
        return found;
    }

    private static Optional<UUID> parse(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String reason(PaymentProvider.Intent intent) {
        return intent.failureMessage() != null ? intent.failureMessage() : intent.failureCode() != null ? intent.failureCode() : "the payment was declined";
    }
}
