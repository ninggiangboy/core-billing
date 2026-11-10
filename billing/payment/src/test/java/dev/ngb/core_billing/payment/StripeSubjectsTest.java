package dev.ngb.core_billing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.payment.adapter.stripe.StripePaymentProvider;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider.ProviderEvent;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The facts Billing reads from Stripe's JSON, from fixtures shaped like Stripe's events. */
class StripeSubjectsTest {

    private static final String PAYMENT = "0198c1a0-7b1e-7c3e-9b52-0d6f6f1c2a10";
    private final StripePaymentProvider provider = new StripePaymentProvider(null, Duration.ofSeconds(1), Duration.ofSeconds(1), 300);

    private static String event(String id, String type, String account, String object) {
        return "{\"id\":\"" + id + "\",\"object\":\"event\",\"type\":\"" + type + "\",\"livemode\":false,\"created\":1789000000"
                + (account == null ? "" : ",\"account\":\"" + account + "\"") + ",\"data\":{\"object\":" + object + "}}";
    }

    @Test
    void aPaidCheckoutSession() {
        ProviderEvent e = provider.interpret(event("evt_1", "checkout.session.completed", null,
                "{\"object\":\"checkout.session\",\"id\":\"cs_1\",\"status\":\"complete\",\"payment_status\":\"paid\",\"amount_total\":2500,\"currency\":\"usd\","
                        + "\"payment_intent\":\"pi_1\",\"metadata\":{\"billing_payment_id\":\"" + PAYMENT + "\"}}"));
        assertThat(e.id()).isEqualTo("evt_1");
        assertThat(e.type()).isEqualTo("checkout.session.completed");
        assertThat(e.account()).isNull();
        assertThat(e.livemode()).isFalse();
        assertThat(e.subject()).isEqualTo(new PaymentProvider.Checkout("cs_1", "pi_1", PAYMENT, PaymentProvider.CheckoutStatus.COMPLETE,
                PaymentProvider.CheckoutPayment.PAID, 2500, "usd"));
    }

    @Test
    void anUnpaidCompletedSessionAndAnExpiredOne() {
        PaymentProvider.Checkout unpaid = (PaymentProvider.Checkout) provider.interpret(event("evt_2", "checkout.session.completed", "acct_9",
                "{\"object\":\"checkout.session\",\"id\":\"cs_2\",\"status\":\"complete\",\"payment_status\":\"unpaid\",\"amount_total\":100,\"currency\":\"eur\","
                        + "\"payment_intent\":{\"id\":\"pi_expanded\"},\"client_reference_id\":\"" + PAYMENT + "\"}")).subject();
        assertThat(unpaid.payment()).isEqualTo(PaymentProvider.CheckoutPayment.UNPAID);
        assertThat(unpaid.paymentIntentId()).as("an expanded intent is read by its id").isEqualTo("pi_expanded");
        assertThat(unpaid.paymentRef()).as("falls back to the client reference").isEqualTo(PAYMENT);

        PaymentProvider.Checkout expired = (PaymentProvider.Checkout) provider.interpret(event("evt_3", "checkout.session.expired", null,
                "{\"object\":\"checkout.session\",\"id\":\"cs_3\",\"status\":\"expired\",\"payment_status\":\"unpaid\",\"amount_total\":100,\"currency\":\"eur\"}")).subject();
        assertThat(expired.status()).isEqualTo(PaymentProvider.CheckoutStatus.EXPIRED);
        assertThat(expired.paymentIntentId()).isNull();
    }

    @Test
    void aDeclinedPaymentIntent() {
        PaymentProvider.Intent intent = (PaymentProvider.Intent) provider.interpret(event("evt_4", "payment_intent.payment_failed", null,
                "{\"object\":\"payment_intent\",\"id\":\"pi_1\",\"status\":\"requires_payment_method\",\"amount\":2500,\"currency\":\"usd\","
                        + "\"metadata\":{\"billing_payment_id\":\"" + PAYMENT + "\"},"
                        + "\"last_payment_error\":{\"code\":\"card_declined\",\"decline_code\":\"insufficient_funds\",\"message\":\"Your card has insufficient funds.\"}}")).subject();
        assertThat(intent.state()).isEqualTo(PaymentProvider.IntentState.REQUIRES_PAYMENT_METHOD);
        assertThat(intent.failureCode()).isEqualTo("insufficient_funds");
        assertThat(intent.failureMessage()).isEqualTo("Your card has insufficient funds.");
        assertThat(intent.paymentRef()).isEqualTo(PAYMENT);
    }

    @Test
    void paymentIntentStates() {
        assertThat(state("succeeded")).isEqualTo(PaymentProvider.IntentState.SUCCEEDED);
        assertThat(state("processing")).isEqualTo(PaymentProvider.IntentState.PROCESSING);
        assertThat(state("requires_action")).isEqualTo(PaymentProvider.IntentState.REQUIRES_ACTION);
        assertThat(state("canceled")).isEqualTo(PaymentProvider.IntentState.CANCELED);
        assertThat(state("something_new")).isEqualTo(PaymentProvider.IntentState.OTHER);
    }

    private PaymentProvider.IntentState state(String status) {
        return ((PaymentProvider.Intent) provider.interpret(event("evt_s", "payment_intent.processing", null,
                "{\"object\":\"payment_intent\",\"id\":\"pi_s\",\"status\":\"" + status + "\",\"amount\":1,\"currency\":\"usd\"}")).subject()).state();
    }

    @Test
    void aRefundCarriesOurReferenceFromItsMetadata() {
        PaymentProvider.RefundObject refund = (PaymentProvider.RefundObject) provider.interpret(event("evt_5", "refund.updated", "acct_9",
                "{\"object\":\"refund\",\"id\":\"re_1\",\"status\":\"succeeded\",\"amount\":500,\"currency\":\"usd\",\"payment_intent\":\"pi_1\","
                        + "\"metadata\":{\"billing_refund_id\":\"" + PAYMENT + "\"}}")).subject();
        assertThat(refund).isEqualTo(new PaymentProvider.RefundObject("re_1", "pi_1", PAYMENT, PaymentProvider.RefundState.SUCCEEDED, 500, "usd", null));
    }

    @Test
    void accountsAndEverythingElse() {
        assertThat(provider.interpret(event("evt_6", "account.updated", "acct_9", "{\"object\":\"account\",\"id\":\"acct_9\"}")).subject())
                .isEqualTo(new PaymentProvider.AccountObject("acct_9"));
        assertThat(provider.interpret(event("evt_7", "charge.refunded", null, "{\"object\":\"charge\",\"id\":\"ch_1\"}")).subject())
                .isInstanceOf(PaymentProvider.Unhandled.class);
    }

    @Test
    void theSdkIsPinnedToTheApiVersionBillingWasWrittenAgainst() {
        // Upgrading the SDK changes this; the mapping above must be re-checked against the new version first.
        assertThat(StripePaymentProvider.sdkApiVersion()).isEqualTo(StripePaymentProvider.PINNED_API_VERSION);
    }
}
