package io.corebilling.payment.application;

import io.corebilling.payment.domain.PaymentId;
import io.corebilling.payment.domain.RefundId;
import io.corebilling.payment.domain.StripeEnvironment;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.List;

/**
 * Everything the payment context asks of a payment provider. Stripe is the only implementation; its types never
 * cross this port, so a second provider is one adapter plus the same verified-event path. Every call that
 * changes something at the provider takes an idempotency key, so a repeat is safe.
 */
public interface PaymentProvider {

    /**
     * The credentials a call is made with. {@code connectedAccountId} is set for Direct charges on a connected
     * account; the secret key is the Platform's then.
     */
    record Access(String secretKey, String connectedAccountId) {
        /** Keeps the key out of logs and exception messages. */
        @Override
        public String toString() {
            return "Access[account=" + connectedAccountId + "]";
        }
    }

    record VerifiedAccount(String accountId, StripeEnvironment environment) {}

    record AccountState(boolean cardPaymentsActive) {}

    enum CheckoutStatus { OPEN, COMPLETE, EXPIRED }

    enum CheckoutPayment { PAID, UNPAID, NO_PAYMENT_REQUIRED }

    enum IntentState { SUCCEEDED, PROCESSING, REQUIRES_PAYMENT_METHOD, REQUIRES_ACTION, CANCELED, OTHER }

    enum RefundState { SUCCEEDED, PENDING, FAILED, CANCELED }

    /** What a Stripe object says, reduced to what Billing decides on. */
    sealed interface Subject permits Checkout, Intent, RefundObject, AccountObject, Unhandled {}

    record Checkout(
            String sessionId, String paymentIntentId, String paymentRef, CheckoutStatus status, CheckoutPayment payment, long amountTotal,
            String currency) implements Subject {}

    record Intent(
            String intentId, String paymentRef, IntentState state, long amount, String currency, String failureCode, String failureMessage)
            implements Subject {}

    record RefundObject(
            String refundId, String intentId, String refundRef, RefundState state, long amount, String currency, String failureReason)
            implements Subject {}

    record AccountObject(String accountId) implements Subject {}

    record Unhandled() implements Subject {}

    /** A signature-verified (or re-read) event. {@code account} is the connected account it happened on, or null. */
    record ProviderEvent(String id, String type, String account, boolean livemode, Instant created, Subject subject) {}

    record CheckoutRequest(
            PaymentId paymentId, TenantId tenantId, Money amount, String description, String successUrl, String cancelUrl) {}

    record CreatedCheckout(String sessionId, String url) {}

    record RefundRequest(RefundId refundId, PaymentId paymentId, String paymentIntentId, Money amount, String reason) {}

    /** Checks the key against the provider with a harmless read and reports which account it belongs to. */
    VerifiedAccount verifyKey(String secretKey);

    /** @throws InvalidSignatureException when the body does not match the signature or the timestamp is stale */
    ProviderEvent verifyWebhook(String payload, String signatureHeader, String webhookSecret);

    /** Reads a stored, already verified payload again. */
    ProviderEvent interpret(String payload);

    String createConnectedAccount(String platformSecretKey, String displayName, String contactEmail, String idempotencyKey);

    String createOnboardingLink(String platformSecretKey, String accountId, String returnUrl, String refreshUrl);

    AccountState retrieveAccount(String platformSecretKey, String accountId);

    CreatedCheckout createCheckout(Access access, CheckoutRequest request, String idempotencyKey);

    Intent retrievePayment(Access access, String paymentIntentId);

    RefundObject createRefund(Access access, RefundRequest request, String idempotencyKey);

    List<Checkout> recentCheckouts(Access access, Instant since);

    List<RefundObject> recentRefunds(Access access, Instant since);
}
