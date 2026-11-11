package dev.ngb.core_billing.support;

import dev.ngb.core_billing.payment.adapter.stripe.provider.StripePaymentProvider;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.stripe.exception.ProviderRejectedException;
import dev.ngb.core_billing.payment.application.stripe.exception.ProviderUnavailableException;
import dev.ngb.core_billing.payment.domain.stripe.StripeEnvironment;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-memory stand-in for the payment provider: everything that would cross the network is recorded and
 * answered from what a test set up. Signature verification and event parsing are the real ones, so tests sign
 * and send events exactly as Stripe would. Running the payment tests against this proves the application layer
 * depends on the {@link PaymentProvider} port and on nothing of Stripe's.
 */
public class FakePaymentProvider implements PaymentProvider {

    public record CheckoutCall(Access access, CheckoutRequest request, String idempotencyKey) {}

    public record RefundCall(Access access, RefundRequest request, String idempotencyKey) {}

    private final StripePaymentProvider real = new StripePaymentProvider(null, Duration.ofSeconds(1), Duration.ofSeconds(1), 300);
    /** Shared by every instance: several Spring contexts talk to one database, and Stripe ids are globally unique. */
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String RUN = java.util.UUID.randomUUID().toString().substring(0, 8);
    private final AtomicInteger counter = COUNTER;

    public final List<CheckoutCall> checkouts = new CopyOnWriteArrayList<>();
    /** How many distinct Checkout Sessions were actually created; a replayed call reuses one. */
    public final AtomicInteger checkoutSessions = new AtomicInteger();
    public final List<RefundCall> refunds = new CopyOnWriteArrayList<>();
    public final List<String> keysVerified = new CopyOnWriteArrayList<>();
    public final Map<String, Intent> intents = new ConcurrentHashMap<>();
    public final List<Checkout> recentCheckouts = new CopyOnWriteArrayList<>();
    public final List<RefundObject> recentRefunds = new CopyOnWriteArrayList<>();
    private final Map<String, CreatedCheckout> checkoutByKey = new ConcurrentHashMap<>();
    private final Map<String, RefundObject> refundByKey = new ConcurrentHashMap<>();
    public volatile boolean cardPaymentsActive = false;
    /** When set, every key verifies to this account (so two tenants can be given the same one). */
    public volatile String accountOverride;
    public volatile RuntimeException failNextCheckout;
    public volatile RuntimeException failNextRefund;

    public void reset() {
        checkouts.clear();
        checkoutSessions.set(0);
        refunds.clear();
        keysVerified.clear();
        intents.clear();
        recentCheckouts.clear();
        recentRefunds.clear();
        checkoutByKey.clear();
        refundByKey.clear();
        cardPaymentsActive = false;
        failNextCheckout = null;
        failNextRefund = null;
        accountOverride = null;
    }

    @Override
    public VerifiedAccount verifyKey(String secretKey) {
        keysVerified.add(secretKey);
        if (secretKey.contains("bad")) {
            throw new ProviderRejectedException("Stripe rejected the credentials");
        }
        // The account follows the key, so two tenants given the same key are given the same account.
        String account = accountOverride != null ? accountOverride : "acct_fake_" + secretKey.substring(secretKey.length() - 8);
        return new VerifiedAccount(account, StripeEnvironment.TEST);
    }

    @Override
    public ProviderEvent verifyWebhook(String payload, String signatureHeader, String webhookSecret) {
        return real.verifyWebhook(payload, signatureHeader, webhookSecret);
    }

    @Override
    public ProviderEvent interpret(String payload) {
        return real.interpret(payload);
    }

    @Override
    public String createConnectedAccount(String platformSecretKey, String displayName, String contactEmail, String idempotencyKey) {
        return "acct_conn_" + counter.incrementAndGet() + "_" + Integer.toHexString(idempotencyKey.hashCode());
    }

    @Override
    public String createOnboardingLink(String platformSecretKey, String accountId, String returnUrl, String refreshUrl) {
        return "https://connect.stripe.test/setup/" + accountId + "/" + counter.incrementAndGet();
    }

    @Override
    public AccountState retrieveAccount(String platformSecretKey, String accountId) {
        return new AccountState(cardPaymentsActive);
    }

    @Override
    public CreatedCheckout createCheckout(Access access, CheckoutRequest request, String idempotencyKey) {
        checkouts.add(new CheckoutCall(access, request, idempotencyKey));
        RuntimeException failure = failNextCheckout;
        if (failure != null) {
            failNextCheckout = null;
            throw failure;
        }
        return checkoutByKey.computeIfAbsent(idempotencyKey, k -> {
            checkoutSessions.incrementAndGet();
            String id = "cs_fake_" + RUN + "_" + counter.incrementAndGet();
            return new CreatedCheckout(id, "https://checkout.stripe.test/pay/" + id);
        });
    }

    @Override
    public Intent retrievePayment(Access access, String paymentIntentId) {
        Intent intent = intents.get(paymentIntentId);
        if (intent == null) {
            throw new ProviderRejectedException("Stripe rejected the request (resource_missing)");
        }
        return intent;
    }

    @Override
    public RefundObject createRefund(Access access, RefundRequest request, String idempotencyKey) {
        refunds.add(new RefundCall(access, request, idempotencyKey));
        RuntimeException failure = failNextRefund;
        if (failure != null) {
            failNextRefund = null;
            throw failure;
        }
        return refundByKey.computeIfAbsent(idempotencyKey, k -> new RefundObject("re_fake_" + RUN + "_" + counter.incrementAndGet(), request.paymentIntentId(),
                request.refundId().toString(), RefundState.PENDING, request.amount().minorUnits(), request.amount().currency().code().toLowerCase(), null));
    }

    @Override
    public List<Checkout> recentCheckouts(Access access, Instant since) {
        return new ArrayList<>(recentCheckouts);
    }

    @Override
    public List<RefundObject> recentRefunds(Access access, Instant since) {
        return new ArrayList<>(recentRefunds);
    }

    public static ProviderUnavailableException unreachable() {
        return new ProviderUnavailableException("Stripe could not be reached or is busy", new java.io.IOException("connection refused"));
    }
}
