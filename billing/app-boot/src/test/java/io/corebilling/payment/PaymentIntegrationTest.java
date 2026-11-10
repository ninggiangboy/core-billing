package io.corebilling.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.Application;
import io.corebilling.money.api.ChargeCommand;
import io.corebilling.money.api.ChargeWallet;
import io.corebilling.money.api.WalletLookup;
import io.corebilling.money.application.WalletQueries;
import io.corebilling.payment.PaymentTestWorld.PayWorld;
import io.corebilling.payment.application.Audit;
import io.corebilling.payment.application.CreateTopUpUseCase;
import io.corebilling.payment.application.PaymentProvider;
import io.corebilling.payment.application.PaymentQueries;
import io.corebilling.payment.application.ProviderRejectedException;
import io.corebilling.payment.application.ProviderUnavailableException;
import io.corebilling.payment.application.ReceiveStripeWebhookUseCase;
import io.corebilling.payment.application.ReceiveStripeWebhookUseCase.Disposition;
import io.corebilling.payment.application.ReconcileStripeUseCase;
import io.corebilling.payment.application.RequestRefundUseCase;
import io.corebilling.payment.application.RetryStripeEventsUseCase;
import io.corebilling.payment.application.StripeEventAdministration;
import io.corebilling.payment.domain.EventStatus;
import io.corebilling.payment.domain.Payment;
import io.corebilling.payment.domain.PaymentId;
import io.corebilling.payment.domain.PaymentStatus;
import io.corebilling.payment.domain.Refund;
import io.corebilling.payment.domain.RefundStatus;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.IdempotencyConflictException;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
import io.corebilling.support.EventSchemas;
import io.corebilling.support.FakePaymentProvider;
import io.corebilling.support.FakePaymentProviderConfig;
import io.corebilling.support.OwnerDatabase;
import io.corebilling.support.PostgresContainerConfig;
import io.corebilling.support.ScopeTestSupport;
import io.corebilling.support.StripeEvents;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The payment flows against real PostgreSQL, with Stripe replaced by an in-memory provider (which keeps real
 * signature checking and event parsing). The assertions are about the books: what the wallet and ledger hold,
 * what was announced, and that nothing changes except from a verified Stripe result.
 */
@SpringBootTest(classes = Application.class)
@Import({PostgresContainerConfig.class, FakePaymentProviderConfig.class})
class PaymentIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");

    /** Stripe ids are globally unique, so every test gets its own. */
    private final String sfx = "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    @Autowired PaymentTestWorld worlds;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired FakePaymentProvider stripe;
    @Autowired CreateTopUpUseCase topUp;
    @Autowired RequestRefundUseCase refund;
    @Autowired ReceiveStripeWebhookUseCase webhooks;
    @Autowired RetryStripeEventsUseCase retry;
    @Autowired ReconcileStripeUseCase reconcile;
    @Autowired io.corebilling.payment.application.StripeConfigurationService configService;
    @Autowired io.corebilling.money.application.ExpireReservationsUseCase expireReservations;
    @Autowired StripeEventAdministration eventAdmin;
    @Autowired PaymentQueries queries;
    @Autowired WalletQueries wallets;
    @Autowired ChargeWallet charge;
    @Autowired WalletLookup walletLookup;

    @BeforeEach
    void resetStripe() {
        stripe.reset();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private Payment startTopUp(PayWorld w, long amount) {
        return startTopUp(w, amount, "key-" + UUID.randomUUID()).payment();
    }

    private CreateTopUpUseCase.Result startTopUp(PayWorld w, long amount, String key) {
        return scopes.inTenant(w.money().tenant().id(), () -> topUp.execute(new CreateTopUpUseCase.Command(
                w.money().wallet().id(), Money.of(amount, w.money().wallet().currency()), key, AUDIT)));
    }

    private ReceiveStripeWebhookUseCase.Receipt deliver(PayWorld w, String body) {
        return webhooks.receiveDirect(w.configuration().id(), body, StripeEvents.sign(w.webhookSecret(), body));
    }

    private ReceiveStripeWebhookUseCase.Receipt paid(PayWorld w, Payment p, String eventId, String intent) {
        return deliver(w, StripeEvents.paidCheckout(eventId, null, p.checkoutSessionId(), intent, p.id().toString(), p.amount().minorUnits()));
    }

    private long balance(PayWorld w) {
        return owner.count("select balance from wallets where id = ?", w.money().wallet().id().value());
    }

    private long held(PayWorld w) {
        return owner.count("select held from wallets where id = ?", w.money().wallet().id().value());
    }

    private long ledgerEntries(PayWorld w) {
        return owner.count("select count(*) from ledger_entries where wallet_id = ?", w.money().wallet().id().value());
    }

    private long outbox(PayWorld w, String type) {
        return owner.count("select count(*) from outbox where tenant_id = ? and type = ?", w.money().tenant().id().value(), type);
    }

    private Payment reload(PayWorld w, Payment p) {
        return scopes.inTenant(w.money().tenant().id(), () -> queries.payment(p.id()).payment());
    }

    private Refund requestRefund(PayWorld w, Payment p, Long amount, String key) {
        Money money = amount == null ? null : Money.of(amount, p.amount().currency());
        return scopes.inTenant(w.money().tenant().id(), () -> refund.execute(new RequestRefundUseCase.Command(p.id(), money, "requested", key, AUDIT)).refund());
    }

    private Refund reloadRefund(PayWorld w, Refund r) {
        return scopes.inTenant(w.money().tenant().id(), () -> queries.refund(r.id()));
    }

    private void refundEvent(PayWorld w, Payment p, Refund r, String status) {
        deliver(w, StripeEvents.refund(StripeEvents.newEventId(), null, r.stripeRefundId(), p.paymentIntentId(), r.id().toString(), status,
                r.amount().minorUnits()));
    }

    /** A payment that was topped up and credited, ready to be refunded. */
    private Payment settledPayment(PayWorld w, long amount) {
        Payment p = startTopUp(w, amount);
        assertThat(paid(w, p, StripeEvents.newEventId(), "pi_" + UUID.randomUUID().toString().replace("-", "")).disposition()).isEqualTo(Disposition.PROCESSED);
        return reload(w, p);
    }

    // ---- creating a top-up ---------------------------------------------------------------------------------------

    @Test
    void aTopUpCreatesAnOpenPaymentAndChangesNoBalance() {
        PayWorld w = worlds.direct(0);

        Payment p = startTopUp(w, 5000);

        assertThat(p.status()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(p.checkoutUrl()).startsWith("https://checkout.stripe.test/");
        assertThat(p.checkoutSessionId()).startsWith("cs_fake_");
        assertThat(balance(w)).isZero();
        assertThat(ledgerEntries(w)).isZero();
        assertThat(stripe.checkouts).hasSize(1);
        assertThat(stripe.checkouts.get(0).request().paymentId()).isEqualTo(p.id());
        assertThat(stripe.checkouts.get(0).access().connectedAccountId()).as("Direct mode charges the Tenant's own account").isNull();
    }

    @Test
    void theSameIdempotencyKeyGivesTheSamePaymentAndCallsStripeOnce() {
        PayWorld w = worlds.direct(0);

        CreateTopUpUseCase.Result first = startTopUp(w, 5000, "same-key");
        CreateTopUpUseCase.Result second = startTopUp(w, 5000, "same-key");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.payment().id()).isEqualTo(first.payment().id());
        assertThat(stripe.checkouts).hasSize(1);
        assertThat(owner.count("select count(*) from payments where tenant_id = ?", w.money().tenant().id().value())).isEqualTo(1);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRefused() {
        PayWorld w = worlds.direct(0);
        startTopUp(w, 5000, "reused");

        assertThatThrownBy(() -> startTopUp(w, 6000, "reused")).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aRetryAfterStripeWasUnreachableCompletesTheSamePayment() {
        PayWorld w = worlds.direct(0);
        stripe.failNextCheckout = FakePaymentProvider.unreachable();

        assertThatThrownBy(() -> startTopUp(w, 5000, "retry-me")).isInstanceOf(ProviderUnavailableException.class);
        assertThat(owner.count("select count(*) from payments where tenant_id = ? and checkout_session_id is null", w.money().tenant().id().value()))
                .isEqualTo(1);

        CreateTopUpUseCase.Result retried = startTopUp(w, 5000, "retry-me");

        assertThat(retried.payment().checkoutUrl()).isNotNull();
        assertThat(owner.count("select count(*) from payments where tenant_id = ?", w.money().tenant().id().value())).isEqualTo(1);
        assertThat(stripe.checkouts).as("both calls used the same Stripe idempotency key").extracting(FakePaymentProvider.CheckoutCall::idempotencyKey)
                .containsOnly("topup-" + retried.payment().id());
    }

    @Test
    void stripeRefusingTheCheckoutClosesThePayment() {
        PayWorld w = worlds.direct(0);
        stripe.failNextCheckout = new ProviderRejectedException("Stripe rejected the request (amount_too_small)");

        assertThatThrownBy(() -> startTopUp(w, 5, "tiny")).isInstanceOf(ProviderRejectedException.class);

        assertThat(owner.count("select count(*) from payments where tenant_id = ? and status = 'FAILED'", w.money().tenant().id().value())).isEqualTo(1);
        assertThat(balance(w)).isZero();
    }

    @Test
    void aTopUpInAnotherCurrencyThanTheWalletIsRefused() {
        PayWorld w = worlds.direct(0);
        assertThatThrownBy(() -> scopes.inTenant(w.money().tenant().id(), () -> topUp.execute(new CreateTopUpUseCase.Command(
                w.money().wallet().id(), Money.of(100, io.corebilling.sharedkernel.Currency.of("EUR")), "k-eur", AUDIT))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTopUpNeedsAConfigurationThatCanTakePayments() {
        PayWorld pending = worlds.direct(0);
        owner.jdbc().update("update stripe_configurations set status = 'PENDING' where id = ?", pending.configuration().id().value());

        assertThatThrownBy(() -> startTopUp(pending, 100, "k-pending")).isInstanceOf(ConflictException.class);
        assertThat(stripe.checkouts).isEmpty();
    }

    // ---- verified results ----------------------------------------------------------------------------------------

    @Test
    void aVerifiedSuccessCreditsTheWalletOnceAndAnnouncesIt() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);

        ReceiveStripeWebhookUseCase.Receipt receipt = paid(w, p, StripeEvents.newEventId(), "pi_1" + sfx);

        assertThat(receipt.disposition()).isEqualTo(Disposition.PROCESSED);
        assertThat(balance(w)).isEqualTo(5000);
        assertThat(ledgerEntries(w)).isEqualTo(1);
        Payment settled = reload(w, p);
        assertThat(settled.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(settled.ledgerEntryId()).isNotNull();
        assertThat(settled.paymentIntentId()).isEqualTo("pi_1" + sfx);
        assertThat(outbox(w, "payment.succeeded")).isEqualTo(1);
        assertThat(outbox(w, "wallet.credited")).isEqualTo(1);
    }

    @Test
    void replayingTheSameEventCreditsTheWalletOnce() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);
        String eventId = StripeEvents.newEventId();

        assertThat(paid(w, p, eventId, "pi_1" + sfx).disposition()).isEqualTo(Disposition.PROCESSED);
        assertThat(paid(w, p, eventId, "pi_1" + sfx).disposition()).isEqualTo(Disposition.DUPLICATE);
        assertThat(paid(w, p, eventId, "pi_1" + sfx).disposition()).isEqualTo(Disposition.DUPLICATE);

        assertThat(balance(w)).isEqualTo(5000);
        assertThat(ledgerEntries(w)).isEqualTo(1);
        assertThat(outbox(w, "payment.succeeded")).isEqualTo(1);
        assertThat(owner.count("select count(*) from stripe_events where tenant_id = ?", w.money().tenant().id().value())).isEqualTo(1);
    }

    @Test
    void twoDifferentEventsAboutTheSamePaymentStillCreditOnce() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);

        paid(w, p, StripeEvents.newEventId(), "pi_1" + sfx);
        ReceiveStripeWebhookUseCase.Receipt second = deliver(w, StripeEvents.intent(StripeEvents.newEventId(), "payment_intent.succeeded", null, "pi_1" + sfx,
                p.id().toString(), "succeeded", 5000, null, null));

        assertThat(second.disposition()).isEqualTo(Disposition.IGNORED);
        assertThat(balance(w)).isEqualTo(5000);
        assertThat(ledgerEntries(w)).isEqualTo(1);
    }

    @Test
    void anIntentSucceededEventBeforeTheSessionCompletedStillCreditsOnce() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);

        assertThat(deliver(w, StripeEvents.intent(StripeEvents.newEventId(), "payment_intent.succeeded", null, "pi_early" + sfx, p.id().toString(), "succeeded",
                5000, null, null)).disposition()).isEqualTo(Disposition.PROCESSED);
        assertThat(paid(w, p, StripeEvents.newEventId(), "pi_early" + sfx).disposition()).isEqualTo(Disposition.IGNORED);

        assertThat(balance(w)).isEqualTo(5000);
        assertThat(ledgerEntries(w)).isEqualTo(1);
    }

    @Test
    void aFailureReportedAfterSuccessLeavesThePaymentSucceeded() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);
        paid(w, p, StripeEvents.newEventId(), "pi_1" + sfx);

        ReceiveStripeWebhookUseCase.Receipt late = deliver(w, StripeEvents.intent(StripeEvents.newEventId(), "payment_intent.payment_failed", null, "pi_1" + sfx,
                p.id().toString(), "requires_payment_method", 5000, "insufficient_funds", "Your card has insufficient funds."));

        assertThat(late.disposition()).isEqualTo(Disposition.IGNORED);
        assertThat(reload(w, p).status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(balance(w)).isEqualTo(5000);
        assertThat(outbox(w, "payment.failed")).isZero();
    }

    @Test
    void aSuccessContradictingAFailedPaymentIsNotAppliedAndNeedsAPerson() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);
        deliver(w, StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.async_payment_failed", null, p.checkoutSessionId(), "pi_1" + sfx,
                p.id().toString(), "complete", "unpaid", 5000, "usd"));
        assertThat(reload(w, p).status()).isEqualTo(PaymentStatus.FAILED);

        ReceiveStripeWebhookUseCase.Receipt contradiction = paid(w, p, StripeEvents.newEventId(), "pi_1" + sfx);

        assertThat(contradiction.disposition()).isEqualTo(Disposition.DEAD);
        assertThat(reload(w, p).status()).as("a final state never regresses").isEqualTo(PaymentStatus.FAILED);
        assertThat(balance(w)).isZero();
        assertThat(owner.count("select count(*) from stripe_incidents where tenant_id = ? and kind = 'EVENT_REJECTED' and resolved_at is null",
                w.money().tenant().id().value())).isEqualTo(1);
    }

    @Test
    void anEventForAPaymentNotYetStoredWaitsAndIsAppliedOnceItExists() {
        PayWorld w = worlds.direct(0);
        PaymentId future = new PaymentId(UUID.randomUUID());
        String body = StripeEvents.paidCheckout(StripeEvents.newEventId(), null, "cs_ahead", "pi_ahead" + sfx, future.toString(), 3000);

        ReceiveStripeWebhookUseCase.Receipt early = deliver(w, body);

        assertThat(early.disposition()).isEqualTo(Disposition.DEFERRED);
        assertThat(balance(w)).isZero();
        assertThat(owner.count("select count(*) from stripe_events where tenant_id = ? and status = 'FAILED' and attempts = 1", w.money().tenant().id().value()))
                .isEqualTo(1);

        worlds.openPayment(w, future, 3000);
        owner.jdbc().update("update stripe_events set next_attempt_at = now() - interval '1 second' where tenant_id = ?", w.money().tenant().id().value());
        RetryStripeEventsUseCase.Round round = scopes.inTenant(w.money().tenant().id(), retry::runOnce);

        assertThat(round.processed()).isEqualTo(1);
        assertThat(balance(w)).isEqualTo(3000);
        assertThat(owner.count("select count(*) from stripe_event_attempts where tenant_id = ?", w.money().tenant().id().value()))
                .as("both attempts are kept as history").isEqualTo(2);
    }

    @Test
    void anEventThatNeverFindsItsPaymentGivesUpAndIsVisible() {
        PayWorld w = worlds.direct(0);
        String body = StripeEvents.paidCheckout(StripeEvents.newEventId(), null, "cs_ghost", "pi_ghost" + sfx, UUID.randomUUID().toString(), 3000);
        deliver(w, body);

        for (int i = 0; i < 3; i++) {
            owner.jdbc().update("update stripe_events set next_attempt_at = now() - interval '1 second' where tenant_id = ? and status = 'FAILED'",
                    w.money().tenant().id().value());
            scopes.inTenant(w.money().tenant().id(), retry::runOnce);
        }

        List<io.corebilling.payment.domain.StripeEvent> dead = scopes.inTenant(w.money().tenant().id(), () -> eventAdmin.list(EventStatus.DEAD, null, 10));
        assertThat(dead).hasSize(1);
        assertThat(dead.get(0).attempts()).isEqualTo(3);
        assertThat(dead.get(0).lastError()).contains("no payment");
        assertThat(scopes.inTenant(w.money().tenant().id(), () -> eventAdmin.incidents(true, 10))).hasSize(1);
    }

    @Test
    void aDeadEventCanBeRetriedByAPersonOnceItsCauseIsFixed() {
        PayWorld w = worlds.direct(0);
        PaymentId late = new PaymentId(UUID.randomUUID());
        deliver(w, StripeEvents.paidCheckout(StripeEvents.newEventId(), null, "cs_late", "pi_late" + sfx, late.toString(), 2000));
        for (int i = 0; i < 2; i++) {
            owner.jdbc().update("update stripe_events set next_attempt_at = now() - interval '1 second' where tenant_id = ? and status = 'FAILED'",
                    w.money().tenant().id().value());
            scopes.inTenant(w.money().tenant().id(), retry::runOnce);
        }
        var dead = scopes.inTenant(w.money().tenant().id(), () -> eventAdmin.list(EventStatus.DEAD, null, 10));
        assertThat(dead).hasSize(1);

        worlds.openPayment(w, late, 2000);
        var detail = scopes.inTenant(w.money().tenant().id(), () -> eventAdmin.retry(AUDIT, dead.get(0).id()));

        assertThat(detail.event().status()).isEqualTo(EventStatus.PROCESSED);
        assertThat(detail.attempts()).extracting(a -> a.outcome()).contains("DEFERRED", "PROCESSED");
        assertThat(balance(w)).isEqualTo(2000);
    }

    @Test
    void aDeclinedAttemptIsRecordedAndThePaymentStaysOpenForAnotherTry() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);

        ReceiveStripeWebhookUseCase.Receipt declined = deliver(w, StripeEvents.intent(StripeEvents.newEventId(), "payment_intent.payment_failed", null,
                "pi_1" + sfx, p.id().toString(), "requires_payment_method", 5000, "insufficient_funds", "Your card has insufficient funds."));

        assertThat(declined.disposition()).isEqualTo(Disposition.PROCESSED);
        Payment open = reload(w, p);
        assertThat(open.status()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(scopes.inTenant(w.money().tenant().id(), () -> queries.payment(p.id()).attempts())).hasSize(1);
        assertThat(outbox(w, "payment.failed")).isZero();

        // The customer tries a second card and it works.
        assertThat(paid(w, p, StripeEvents.newEventId(), "pi_1" + sfx).disposition()).isEqualTo(Disposition.PROCESSED);
        assertThat(balance(w)).isEqualTo(5000);
    }

    @Test
    void anExpiredSessionAfterADeclineFailsThePaymentAndOneWithoutCancelsIt() {
        PayWorld w = worlds.direct(0);
        Payment declined = startTopUp(w, 5000);
        deliver(w, StripeEvents.intent(StripeEvents.newEventId(), "payment_intent.payment_failed", null, "pi_d" + sfx, declined.id().toString(),
                "requires_payment_method", 5000, "card_declined", "Your card was declined."));
        Payment abandoned = startTopUp(w, 700);

        deliver(w, StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.expired", null, declined.checkoutSessionId(), null,
                declined.id().toString(), "expired", "unpaid", 5000, "usd"));
        deliver(w, StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.expired", null, abandoned.checkoutSessionId(), null,
                abandoned.id().toString(), "expired", "unpaid", 700, "usd"));

        assertThat(reload(w, declined).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reload(w, declined).failureReason()).isEqualTo("Your card was declined.");
        assertThat(reload(w, abandoned).status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(outbox(w, "payment.failed")).isEqualTo(1);
        assertThat(balance(w)).isZero();
    }

    @Test
    void aDelayedPaymentMethodIsProcessingUntilItSettles() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);
        stripe.intents.put("pi_bank" + sfx, new PaymentProvider.Intent("pi_bank" + sfx, p.id().toString(), PaymentProvider.IntentState.PROCESSING, 5000, "usd", null, null));

        deliver(w, StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.completed", null, p.checkoutSessionId(), "pi_bank" + sfx, p.id().toString(),
                "complete", "unpaid", 5000, "usd"));
        assertThat(reload(w, p).status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(balance(w)).isZero();

        deliver(w, StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.async_payment_succeeded", null, p.checkoutSessionId(), "pi_bank" + sfx,
                p.id().toString(), "complete", "paid", 5000, "usd"));
        assertThat(reload(w, p).status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(balance(w)).isEqualTo(5000);
    }

    @Test
    void aDelayedPaymentThatFailsFailsThePaymentWithoutCrediting() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);

        deliver(w, StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.async_payment_failed", null, p.checkoutSessionId(), "pi_bank" + sfx,
                p.id().toString(), "complete", "unpaid", 5000, "usd"));

        assertThat(reload(w, p).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(balance(w)).isZero();
        assertThat(outbox(w, "payment.failed")).isEqualTo(1);
    }

    @Test
    void anAmountThatDiffersFromTheRequestedOneCreditsNothing() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);

        ReceiveStripeWebhookUseCase.Receipt receipt = deliver(w, StripeEvents.paidCheckout(StripeEvents.newEventId(), null, p.checkoutSessionId(), "pi_1" + sfx,
                p.id().toString(), 9999));

        assertThat(receipt.disposition()).isEqualTo(Disposition.DEAD);
        assertThat(balance(w)).isZero();
        assertThat(reload(w, p).status()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
    }

    @Test
    void anEventNamingAPaymentOfAnotherTenantCannotTouchIt() {
        PayWorld attacker = worlds.direct(0);
        PayWorld victim = worlds.direct(0);
        Payment victimPayment = startTopUp(victim, 5000);

        // Validly signed for the attacker's own endpoint, but naming the victim's payment.
        ReceiveStripeWebhookUseCase.Receipt receipt = deliver(attacker, StripeEvents.paidCheckout(StripeEvents.newEventId(), null, "cs_x", "pi_x" + sfx,
                victimPayment.id().toString(), 5000));

        assertThat(receipt.disposition()).isEqualTo(Disposition.DEFERRED);
        assertThat(balance(victim)).isZero();
        assertThat(reload(victim, victimPayment).status()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
    }

    @Test
    void aDisconnectedConfigurationStillSettlesWhatItStarted() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);
        scopes.runInTenant(w.money().tenant().id(), () -> configService.disconnect(AUDIT));

        // The customer finishes paying on a Checkout page that was still open.
        assertThat(paid(w, p, StripeEvents.newEventId(), "pi_late" + sfx).disposition()).isEqualTo(Disposition.PROCESSED);

        assertThat(balance(w)).isEqualTo(5000);
        assertThat(scopes.inTenant(w.money().tenant().id(), () -> configService.current())).isEmpty();
    }

    @Test
    void anotherTenantCannotTakeAnAccountWhosePaymentsAreStillInFlight() {
        PayWorld w = worlds.direct(0);
        scopes.runInTenant(w.money().tenant().id(), () -> configService.disconnect(AUDIT));
        PayWorld other = worlds.direct(0);
        String keyForSameAccount = "sk_test_other_tenant_" + w.configuration().accountId().substring(w.configuration().accountId().length() - 8);
        stripe.accountOverride = w.configuration().accountId();

        assertThatThrownBy(() -> scopes.inTenant(other.money().tenant().id(), () -> configService.connectDirect(AUDIT, keyForSameAccount, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void replacingTheKeyOfTheSameAccountKeepsPaymentsAndRefundsWorking() {
        io.corebilling.money.MoneyTestWorld.World money = worlds.tenantWithoutStripe(0);
        String account = "acct_fake_ROTATE" + sfx.substring(1, 3);
        PayWorld first = worlds.directFor(money, account);
        Payment p = startTopUp(first, 5000);
        String rotatedKey = "sk_test_rotated_key_" + account.substring(account.length() - 8);
        var second = scopes.inTenant(money.tenant().id(), () -> configService.connectDirect(AUDIT, rotatedKey, null, "whsec_second" + sfx));
        PayWorld rotated = new PayWorld(money, second, "whsec_second" + sfx);

        assertThat(second.id()).isNotEqualTo(first.configuration().id());
        assertThatThrownBy(() -> paid(first, p, StripeEvents.newEventId(), "pi_x" + sfx)).as("the old endpoint is gone")
                .isInstanceOf(io.corebilling.platform.api.NotFoundException.class);
        assertThat(paid(rotated, p, StripeEvents.newEventId(), "pi_rot" + sfx).disposition()).as("the new endpoint settles the old payment")
                .isEqualTo(Disposition.PROCESSED);
        assertThat(balance(rotated)).isEqualTo(5000);

        Refund requested = requestRefund(rotated, reload(rotated, p), 1000L, "rot-refund");
        assertThat(requested.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(stripe.refunds.get(0).access().secretKey()).as("refunded through the current key").isEqualTo(rotatedKey);
    }

    @Test
    void stripeActivityBillingDidNotStartIsIgnoredWithoutRetriesOrIncidents() {
        PayWorld w = worlds.direct(0);

        var session = deliver(w, StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.completed", null, "cs_other_app", "pi_other_app" + sfx, null,
                "complete", "paid", 100, "usd"));
        var intent = deliver(w, StripeEvents.intent(StripeEvents.newEventId(), "payment_intent.succeeded", null, "pi_foreign" + sfx, null, "succeeded", 100,
                null, null));
        var refund = deliver(w, StripeEvents.refund(StripeEvents.newEventId(), null, "re_foreign", "pi_foreign" + sfx, null, "succeeded", 100));

        assertThat(List.of(session.disposition(), intent.disposition(), refund.disposition())).containsOnly(Disposition.IGNORED);
        assertThat(owner.count("select count(*) from stripe_events where tenant_id = ? and status = 'IGNORED'", w.money().tenant().id().value())).isEqualTo(3);
        assertThat(owner.count("select count(*) from stripe_incidents where tenant_id = ?", w.money().tenant().id().value())).isZero();
        assertThat(balance(w)).isZero();
    }

    @Test
    void anEventLeftUnprocessedByAStoppedProcessIsPickedUpByTheRetryJob() {
        PayWorld w = worlds.direct(0);
        PaymentId late = new PaymentId(UUID.randomUUID());
        deliver(w, StripeEvents.paidCheckout(StripeEvents.newEventId(), null, "cs_stuck" + sfx, "pi_stuck" + sfx, late.toString(), 1200));
        worlds.openPayment(w, late, 1200);
        owner.jdbc().update("update stripe_events set status = 'RECEIVED', next_attempt_at = null, received_at = now() - interval '10 minutes' "
                + "where tenant_id = ?", w.money().tenant().id().value());

        RetryStripeEventsUseCase.Round round = scopes.inTenant(w.money().tenant().id(), retry::runOnce);

        assertThat(round.processed()).isEqualTo(1);
        assertThat(balance(w)).isEqualTo(1200);
    }

    // ---- hold expiry ----------------------------------------------------------------------------------------------

    @Test
    void aRefundStripeSettlesAfterItsHoldExpiredIsStillDebitedWhileTheBalanceIsThere() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        Refund requested = requestRefund(w, p, null, "late-1");
        expireHolds(w, requested);

        refundEvent(w, p, requested, "succeeded");

        assertThat(reloadRefund(w, requested).status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(balance(w)).isZero();
        assertThat(held(w)).isZero();
    }

    @Test
    void aRefundSettledAfterItsHoldExpiredAndTheBalanceWasSpentIsFlaggedAndNeverOverdrawsTheWallet() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        Refund requested = requestRefund(w, p, null, "late-2");
        expireHolds(w, requested);
        scopes.inTenant(w.money().tenant().id(), () -> charge.charge(new ChargeCommand(w.money().wallet().id(), null,
                Money.of(4000, p.amount().currency()), "usage-late", null, "spend-late")));

        var receipt = deliver(w, StripeEvents.refund(StripeEvents.newEventId(), null, requested.stripeRefundId(), p.paymentIntentId(),
                requested.id().toString(), "succeeded", 5000));

        assertThat(receipt.disposition()).isEqualTo(Disposition.DEFERRED);
        assertThat(balance(w)).as("the wallet is not driven negative").isEqualTo(1000);
        assertThat(reloadRefund(w, requested).status()).as("the refund stays open for a person").isEqualTo(RefundStatus.PENDING);
        assertThat(owner.count("select count(*) from stripe_events where tenant_id = ? and status = 'FAILED' and last_error like '%Insufficient%'",
                w.money().tenant().id().value())).isEqualTo(1);
    }

    private void expireHolds(PayWorld w, Refund requested) {
        owner.jdbc().update("update reservations set expires_at = now() - interval '1 second', created_at = now() - interval '1 minute' where id = ?",
                requested.reservationId());
        scopes.inTenant(w.money().tenant().id(), () -> expireReservations.execute(10));
        assertThat(held(w)).as("the hold is gone").isZero();
    }

    @Test
    void aRefundWhoseOutcomeStaysUnknownForHalfADayCannotBeRepeated() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        stripe.failNextRefund = FakePaymentProvider.unreachable();
        assertThatThrownBy(() -> requestRefund(w, p, null, "old-1")).isInstanceOf(ProviderUnavailableException.class);
        owner.jdbc().update("update refunds set created_at = now() - interval '13 hours' where tenant_id = ?", w.money().tenant().id().value());

        assertThatThrownBy(() -> requestRefund(w, p, null, "old-1")).isInstanceOf(ConflictException.class).hasMessageContaining("unknown");

        assertThat(stripe.refunds).hasSize(1);
    }

    @Test
    void reconciliationCarriesOnPastAnItemItCannotExamine() {
        PayWorld w = worlds.direct(0);
        Payment unreadable = startTopUp(w, 900);
        Payment fine = startTopUp(w, 5000);
        stripe.recentCheckouts.add(new PaymentProvider.Checkout(unreadable.checkoutSessionId(), "pi_unknown_to_stripe" + sfx, unreadable.id().toString(),
                PaymentProvider.CheckoutStatus.COMPLETE, PaymentProvider.CheckoutPayment.UNPAID, 900, "usd"));
        stripe.recentCheckouts.add(new PaymentProvider.Checkout(fine.checkoutSessionId(), "pi_fine" + sfx, fine.id().toString(),
                PaymentProvider.CheckoutStatus.COMPLETE, PaymentProvider.CheckoutPayment.PAID, 5000, "usd"));

        ReconcileStripeUseCase.Report report = scopes.inTenant(w.money().tenant().id(), reconcile::runOnce);

        assertThat(report.corrected()).isEqualTo(1);
        assertThat(balance(w)).isEqualTo(5000);
        assertThat(scopes.inTenant(w.money().tenant().id(), () -> eventAdmin.incidents(true, 10))).extracting(i -> i.subject())
                .anyMatch(subject -> subject.startsWith("checkout/" + unreadable.checkoutSessionId()));
    }

    // ---- refunds -------------------------------------------------------------------------------------------------

    @Test
    void aRefundHoldsTheBalanceAtOnceAndDebitsOnlyWhenStripeConfirms() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);

        Refund requested = requestRefund(w, p, null, "refund-1");

        assertThat(requested.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(requested.amount()).isEqualTo(Money.of(5000, p.amount().currency()));
        assertThat(requested.stripeRefundId()).startsWith("re_fake_");
        assertThat(balance(w)).as("the ledger is untouched until Stripe confirms").isEqualTo(5000);
        assertThat(held(w)).as("but the unspent balance is held").isEqualTo(5000);
        assertThat(stripe.refunds).hasSize(1);
        assertThat(stripe.refunds.get(0).idempotencyKey()).isEqualTo("refund-" + requested.id());

        refundEvent(w, p, requested, "succeeded");

        assertThat(balance(w)).isZero();
        assertThat(held(w)).isZero();
        Refund done = reloadRefund(w, requested);
        assertThat(done.status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(done.ledgerEntryId()).isNotNull();
        assertThat(reload(w, p).refunded()).isEqualTo(Money.of(5000, p.amount().currency()));
        assertThat(outbox(w, "refund.succeeded")).isEqualTo(1);
        assertThat(outbox(w, "wallet.debited")).isEqualTo(1);
    }

    @Test
    void aRepeatedRefundEventDebitsOnce() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        Refund requested = requestRefund(w, p, 2000L, "refund-1");

        refundEvent(w, p, requested, "succeeded");
        refundEvent(w, p, requested, "succeeded");

        assertThat(balance(w)).isEqualTo(3000);
        assertThat(ledgerEntries(w)).isEqualTo(2);
        assertThat(outbox(w, "refund.succeeded")).isEqualTo(1);
    }

    @Test
    void aPartialThenAFullRefundStaysWithinTheCapturedAmount() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 10_000);

        Refund first = requestRefund(w, p, 4000L, "r-1");
        refundEvent(w, p, first, "succeeded");
        assertThatThrownBy(() -> requestRefund(w, p, 7000L, "r-2")).isInstanceOf(ConflictException.class);
        Refund rest = requestRefund(w, p, null, "r-3");

        assertThat(rest.amount()).as("omitting the amount refunds what is left").isEqualTo(Money.of(6000, p.amount().currency()));
        refundEvent(w, p, rest, "succeeded");
        assertThat(reload(w, p).refunded()).isEqualTo(p.amount());
        assertThat(balance(w)).isZero();
        assertThatThrownBy(() -> requestRefund(w, p, 1L, "r-4")).isInstanceOf(ConflictException.class);
    }

    @Test
    void refundsInFlightCountAgainstTheCapturedAmount() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 10_000);

        requestRefund(w, p, 6000L, "r-1");

        assertThatThrownBy(() -> requestRefund(w, p, 4001L, "r-2")).isInstanceOf(ConflictException.class);
        assertThat(requestRefund(w, p, 4000L, "r-3").status()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void aRefundBeyondTheUnspentBalanceIsRefusedBeforeStripeIsAsked() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        scopes.inTenant(w.money().tenant().id(), () -> charge.charge(new ChargeCommand(w.money().wallet().id(), null,
                Money.of(3000, p.amount().currency()), "usage-1", null, "spend-1")));

        assertThatThrownBy(() -> requestRefund(w, p, null, "r-1")).isInstanceOf(ConflictException.class).hasMessageContaining("unspent");

        assertThat(stripe.refunds).isEmpty();
        assertThat(owner.count("select count(*) from refunds where tenant_id = ?", w.money().tenant().id().value())).isZero();
        assertThat(held(w)).isZero();
        assertThat(requestRefund(w, p, 2000L, "r-2").status()).as("what is left unspent can be refunded").isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void theBalanceHeldForARefundCannotBeSpentMeanwhile() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        requestRefund(w, p, null, "r-1");

        var attempt = scopes.inTenant(w.money().tenant().id(), () -> charge.charge(new ChargeCommand(w.money().wallet().id(), null,
                Money.of(1, p.amount().currency()), "usage-2", null, "spend-2")));

        assertThat(attempt.status()).isEqualTo(io.corebilling.money.api.ChargeStatus.FAILED);
    }

    @Test
    void aFailedRefundReleasesTheHold() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        Refund requested = requestRefund(w, p, null, "r-1");

        refundEvent(w, p, requested, "failed");

        assertThat(reloadRefund(w, requested).status()).isEqualTo(RefundStatus.FAILED);
        assertThat(held(w)).isZero();
        assertThat(balance(w)).isEqualTo(5000);
        assertThat(reload(w, p).refunded().isZero()).isTrue();
        assertThat(requestRefund(w, p, null, "r-2").status()).as("the full amount can be tried again").isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void stripeRefusingARefundReleasesTheHoldAndRecordsTheFailure() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        stripe.failNextRefund = new ProviderRejectedException("Stripe rejected the request (charge_already_refunded)");

        assertThatThrownBy(() -> requestRefund(w, p, null, "r-1")).isInstanceOf(ProviderRejectedException.class);

        assertThat(held(w)).isZero();
        assertThat(owner.count("select count(*) from refunds where tenant_id = ? and status = 'FAILED'", w.money().tenant().id().value())).isEqualTo(1);
    }

    @Test
    void anUnreachableStripeLeavesTheRefundPendingAndTheSameKeyFinishesIt() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        stripe.failNextRefund = FakePaymentProvider.unreachable();

        assertThatThrownBy(() -> requestRefund(w, p, null, "r-1")).isInstanceOf(ProviderUnavailableException.class);
        assertThat(owner.count("select count(*) from refunds where tenant_id = ? and status = 'PENDING' and stripe_refund_id is null",
                w.money().tenant().id().value())).isEqualTo(1);
        assertThat(held(w)).isEqualTo(5000);

        Refund finished = requestRefund(w, p, null, "r-1");

        assertThat(finished.stripeRefundId()).isNotNull();
        assertThat(owner.count("select count(*) from refunds where tenant_id = ?", w.money().tenant().id().value())).isEqualTo(1);
        assertThat(held(w)).as("still one hold, not two").isEqualTo(5000);
    }

    @Test
    void aRefundIsIdempotent() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);

        Refund first = requestRefund(w, p, 1000L, "same");
        Refund second = requestRefund(w, p, 1000L, "same");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(stripe.refunds).hasSize(1);
        assertThat(held(w)).isEqualTo(1000);
        assertThatThrownBy(() -> requestRefund(w, p, 2000L, "same")).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void onlyASucceededPaymentCanBeRefunded() {
        PayWorld w = worlds.direct(0);
        Payment open = startTopUp(w, 5000);

        assertThatThrownBy(() -> requestRefund(w, open, null, "r-1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void aRefundMadeInTheStripeDashboardIsFlaggedNotSilentlyApplied() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);

        deliver(w, StripeEvents.refund(StripeEvents.newEventId(), null, "re_dashboard", p.paymentIntentId(), null, "succeeded", 1000));

        assertThat(balance(w)).as("Billing does not guess which wallet movement a foreign refund implies").isEqualTo(5000);
        assertThat(scopes.inTenant(w.money().tenant().id(), () -> eventAdmin.incidents(true, 10))).extracting(i -> i.kind().name())
                .contains("RECONCILIATION_MISMATCH");
    }

    // ---- reconciliation ------------------------------------------------------------------------------------------

    @Test
    void reconciliationAppliesAPaymentWhoseWebhookWasLostThroughTheVerifiedPath() {
        PayWorld w = worlds.direct(0);
        Payment p = startTopUp(w, 5000);
        stripe.recentCheckouts.add(new PaymentProvider.Checkout(p.checkoutSessionId(), "pi_lost" + sfx, p.id().toString(),
                PaymentProvider.CheckoutStatus.COMPLETE, PaymentProvider.CheckoutPayment.PAID, 5000, "usd"));

        ReconcileStripeUseCase.Report report = scopes.inTenant(w.money().tenant().id(), reconcile::runOnce);

        assertThat(report.corrected()).isEqualTo(1);
        assertThat(reload(w, p).status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(balance(w)).isEqualTo(5000);
        assertThat(outbox(w, "payment.succeeded")).isEqualTo(1);
        assertThat(scopes.inTenant(w.money().tenant().id(), () -> eventAdmin.incidents(true, 10))).hasSize(1);

        ReconcileStripeUseCase.Report again = scopes.inTenant(w.money().tenant().id(), reconcile::runOnce);
        assertThat(again.corrected()).isZero();
        assertThat(balance(w)).as("a second run changes nothing").isEqualTo(5000);
    }

    @Test
    void reconciliationFlagsButNeverUndoesAPaymentStripeDoesNotConfirm() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        stripe.recentCheckouts.add(new PaymentProvider.Checkout(p.checkoutSessionId(), p.paymentIntentId(), p.id().toString(),
                PaymentProvider.CheckoutStatus.EXPIRED, PaymentProvider.CheckoutPayment.UNPAID, 5000, "usd"));

        ReconcileStripeUseCase.Report report = scopes.inTenant(w.money().tenant().id(), reconcile::runOnce);

        assertThat(report.mismatches()).isEqualTo(1);
        assertThat(report.corrected()).isZero();
        assertThat(balance(w)).isEqualTo(5000);
        assertThat(reload(w, p).status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void reconciliationFinishesARefundWhoseWebhookWasLost() {
        PayWorld w = worlds.direct(0);
        Payment p = settledPayment(w, 5000);
        Refund requested = requestRefund(w, p, 2000L, "r-1");
        stripe.recentRefunds.add(new PaymentProvider.RefundObject(requested.stripeRefundId(), p.paymentIntentId(), requested.id().toString(),
                PaymentProvider.RefundState.SUCCEEDED, 2000, "usd", null));

        ReconcileStripeUseCase.Report report = scopes.inTenant(w.money().tenant().id(), reconcile::runOnce);

        assertThat(report.corrected()).isEqualTo(1);
        assertThat(reloadRefund(w, requested).status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(balance(w)).isEqualTo(3000);
        assertThat(held(w)).isZero();
    }

    @Test
    void reconciliationLeavesAlonePaymentsThatAreNotBillingsOwn() {
        PayWorld w = worlds.direct(0);
        stripe.recentCheckouts.add(new PaymentProvider.Checkout("cs_other_app", "pi_other" + sfx, UUID.randomUUID().toString(),
                PaymentProvider.CheckoutStatus.COMPLETE, PaymentProvider.CheckoutPayment.PAID, 100, "usd"));
        stripe.recentCheckouts.add(new PaymentProvider.Checkout("cs_no_ref", null, null, PaymentProvider.CheckoutStatus.COMPLETE,
                PaymentProvider.CheckoutPayment.PAID, 100, "usd"));

        ReconcileStripeUseCase.Report report = scopes.inTenant(w.money().tenant().id(), reconcile::runOnce);

        assertThat(report.corrected()).isZero();
        assertThat(report.mismatches()).isZero();
        assertThat(balance(w)).isZero();
    }

    // ---- isolation -----------------------------------------------------------------------------------------------

    @Test
    void aTenantCannotSeeAnotherTenantsPaymentsOrEvents() {
        PayWorld a = worlds.direct(0);
        PayWorld b = worlds.direct(0);
        Payment p = startTopUp(a, 5000);
        paid(a, p, StripeEvents.newEventId(), "pi_1" + sfx);

        assertThat(scopes.inTenant(b.money().tenant().id(), () -> queries.list((WalletId) null, null, null, 50))).isEmpty();
        assertThat(scopes.inTenant(b.money().tenant().id(), () -> eventAdmin.list(null, null, 50))).isEmpty();
        assertThatThrownBy(() -> scopes.inTenant(b.money().tenant().id(), () -> queries.payment(p.id())))
                .isInstanceOf(io.corebilling.platform.api.NotFoundException.class);
    }

    // ---- contract ------------------------------------------------------------------------------------------------

    @Test
    void thePaymentEventsMatchTheirPublishedSchemas() {
        PayWorld w = worlds.direct(0);
        Payment ok = startTopUp(w, 5000);
        paid(w, ok, StripeEvents.newEventId(), "pi_ok" + sfx);
        Payment bad = startTopUp(w, 700);
        deliver(w, StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.async_payment_failed", null, bad.checkoutSessionId(), "pi_bad" + sfx,
                bad.id().toString(), "complete", "unpaid", 700, "usd"));
        Refund r = requestRefund(w, reload(w, ok), 1000L, "r-1");
        refundEvent(w, reload(w, ok), r, "succeeded");

        Map<String, String> payloads = new java.util.LinkedHashMap<>();
        owner.jdbc().query("select type, payload::text as payload from outbox where tenant_id = ? and type in ('payment.succeeded','payment.failed','refund.succeeded')",
                rs -> {
                    payloads.put(rs.getString("type"), rs.getString("payload"));
                }, w.money().tenant().id().value());

        assertThat(payloads).containsOnlyKeys("payment.succeeded", "payment.failed", "refund.succeeded");
        payloads.forEach((type, json) -> assertThat(EventSchemas.validate(type, json)).as(type).isEmpty());
    }
}
