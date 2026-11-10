package io.corebilling.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.payment.domain.ConnectionStatus;
import io.corebilling.payment.domain.Payment;
import io.corebilling.payment.domain.PaymentId;
import io.corebilling.payment.domain.PaymentStatus;
import io.corebilling.payment.domain.Refund;
import io.corebilling.payment.domain.RefundId;
import io.corebilling.payment.domain.RefundStatus;
import io.corebilling.payment.domain.StripeConfiguration;
import io.corebilling.payment.domain.StripeConfigurationId;
import io.corebilling.payment.domain.StripeEnvironment;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final Currency USD = Currency.of("USD");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    private static StripeConfiguration activeDirect() {
        return StripeConfiguration.direct(new StripeConfigurationId(UUID.randomUUID()), TENANT, "acct_1", StripeEnvironment.TEST, "cipher-key",
                "cipher-whsec", "pk_test_1", NOW);
    }

    private static Payment payment(long amount) {
        return Payment.start(new PaymentId(UUID.randomUUID()), activeDirect(), new WalletId(UUID.randomUUID()), new CustomerId(UUID.randomUUID()),
                Money.of(amount, USD), "key-1", "hash", NOW);
    }

    private static Payment succeeded(long amount) {
        Payment p = payment(amount);
        assertThat(p.succeed()).isEqualTo(Payment.Change.APPLIED);
        p.recordLedgerEntry(UUID.randomUUID(), NOW);
        return p;
    }

    @Test
    void aPaymentOnlyMovesForward() {
        Payment p = payment(1000);
        assertThat(p.markProcessing(NOW)).isEqualTo(Payment.Change.APPLIED);
        assertThat(p.markProcessing(NOW)).isEqualTo(Payment.Change.STALE);
        assertThat(p.fail("declined", NOW)).isEqualTo(Payment.Change.APPLIED);
        assertThat(p.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.completedAt()).isEqualTo(NOW);
    }

    @Test
    void finalStatesNeverChangeAndAStragglerIsHarmless() {
        Payment p = succeeded(1000);
        assertThat(p.markProcessing(NOW)).as("a late 'processing' after success").isEqualTo(Payment.Change.STALE);
        assertThat(p.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(p.fail("late failure", NOW)).as("a different final state is a contradiction, not an update").isEqualTo(Payment.Change.CONTRADICTION);
        assertThat(p.cancel(NOW)).isEqualTo(Payment.Change.CONTRADICTION);
        assertThat(p.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(p.failureReason()).isNull();
    }

    @Test
    void successAfterFailureIsAContradictionNotARegression() {
        Payment p = payment(1000);
        p.fail("declined", NOW);
        assertThat(p.succeed()).isEqualTo(Payment.Change.CONTRADICTION);
        assertThat(p.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void successIsRepeatableWithoutChange() {
        Payment p = succeeded(1000);
        assertThat(p.succeed()).isEqualTo(Payment.Change.STALE);
    }

    @Test
    void theFirstPaymentIntentWins() {
        Payment p = payment(1000);
        assertThat(p.attachIntent("pi_1", NOW)).isTrue();
        assertThat(p.attachIntent("pi_1", NOW)).isTrue();
        assertThat(p.attachIntent("pi_2", NOW)).isFalse();
        assertThat(p.paymentIntentId()).isEqualTo("pi_1");
    }

    @Test
    void refundsCannotExceedTheCapturedAmount() {
        Payment p = succeeded(1000);
        p.addRefunded(Money.of(400, USD), NOW);
        assertThat(p.refundable()).isEqualTo(Money.of(600, USD));
        assertThatThrownBy(() -> p.addRefunded(Money.of(601, USD), NOW)).isInstanceOf(IllegalStateException.class);
        p.addRefunded(Money.of(600, USD), NOW);
        assertThat(p.refundable().isZero()).isTrue();
    }

    @Test
    void onlyASucceededPaymentCanBeRefunded() {
        Payment open = payment(1000);
        assertThatThrownBy(() -> Refund.request(new RefundId(UUID.randomUUID()), open, Money.of(100, USD), null, null, "user/1", "k", "h", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRefundSettlesOnce() {
        Payment p = succeeded(1000);
        Refund refund = Refund.request(new RefundId(UUID.randomUUID()), p, Money.of(100, USD), "  duplicate  ", UUID.randomUUID(), "user/1", "k", "h", NOW);
        assertThat(refund.reason()).isEqualTo("duplicate");
        assertThat(refund.succeed(UUID.randomUUID(), NOW)).isTrue();
        assertThat(refund.succeed(UUID.randomUUID(), NOW)).as("a repeat changes nothing").isFalse();
        assertThat(refund.fail("late", NOW)).isFalse();
        assertThat(refund.status()).isEqualTo(RefundStatus.SUCCEEDED);
    }

    @Test
    void aDirectConfigurationIsActiveOnlyOnceItHasAWebhookSecret() {
        StripeConfiguration c = StripeConfiguration.direct(new StripeConfigurationId(UUID.randomUUID()), TENANT, "acct_1", StripeEnvironment.TEST,
                "cipher-key", null, null, NOW);
        assertThat(c.status()).isEqualTo(ConnectionStatus.PENDING);
        assertThat(c.canTakePayments()).isFalse();
        assertThat(c.connectedAt()).isNull();
        c.setWebhookSecret("cipher-whsec", NOW.plusSeconds(5));
        assertThat(c.status()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(c.connectedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void aConnectedAccountFollowsStripesCapabilityStatus() {
        StripeConfiguration c = StripeConfiguration.connect(new StripeConfigurationId(UUID.randomUUID()), TENANT, "acct_2", StripeEnvironment.TEST, NOW);
        assertThat(c.status()).isEqualTo(ConnectionStatus.PENDING);
        c.observeAccount(false, NOW.plusSeconds(1));
        assertThat(c.status()).as("onboarding unfinished").isEqualTo(ConnectionStatus.PENDING);
        c.observeAccount(true, NOW.plusSeconds(2));
        assertThat(c.status()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(c.connectedAt()).isEqualTo(NOW.plusSeconds(2));
        c.observeAccount(false, NOW.plusSeconds(3));
        assertThat(c.status()).as("Stripe restricted an account that was active").isEqualTo(ConnectionStatus.RESTRICTED);
        assertThat(c.canTakePayments()).isFalse();
    }

    @Test
    void aConnectConfigurationHoldsNoSecretOfTheTenant() {
        StripeConfiguration c = StripeConfiguration.connect(new StripeConfigurationId(UUID.randomUUID()), TENANT, "acct_2", StripeEnvironment.TEST, NOW);
        assertThatThrownBy(() -> c.setWebhookSecret("x", NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(c.secretKeyCiphertext()).isNull();
        assertThat(c.hasWebhookSecret()).isFalse();
    }

    @Test
    void aDisconnectedConfigurationCannotBeChanged() {
        StripeConfiguration c = activeDirect();
        c.disconnect(NOW.plusSeconds(1));
        assertThat(c.status().isLive()).isFalse();
        assertThat(c.disconnectedAt()).isNotNull();
        assertThatThrownBy(() -> c.observeAccount(true, NOW)).isInstanceOf(IllegalStateException.class);
    }
}
