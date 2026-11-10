package dev.ngb.core_billing.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Builds Stripe-shaped event bodies and signs them the way Stripe signs a webhook. */
public final class StripeEvents {

    private StripeEvents() {}

    public static String newEventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String event(String id, String type, String account, String object) {
        return "{\"id\":\"" + id + "\",\"object\":\"event\",\"type\":\"" + type + "\",\"livemode\":false,\"created\":" + Instant.now().getEpochSecond()
                + (account == null ? "" : ",\"account\":\"" + account + "\"") + ",\"data\":{\"object\":" + object + "}}";
    }

    public static String checkout(String eventId, String type, String account, String sessionId, String intentId, String paymentRef, String status,
            String paymentStatus, long amount, String currency) {
        return event(eventId, type, account, "{\"object\":\"checkout.session\",\"id\":\"" + sessionId + "\",\"status\":\"" + status
                + "\",\"payment_status\":\"" + paymentStatus + "\",\"amount_total\":" + amount + ",\"currency\":\"" + currency + "\""
                + (intentId == null ? "" : ",\"payment_intent\":\"" + intentId + "\"")
                + (paymentRef == null ? "" : ",\"metadata\":{\"billing_payment_id\":\"" + paymentRef + "\"}") + "}");
    }

    public static String paidCheckout(String eventId, String account, String sessionId, String intentId, String paymentRef, long amount) {
        return checkout(eventId, "checkout.session.completed", account, sessionId, intentId, paymentRef, "complete", "paid", amount, "usd");
    }

    public static String intent(String eventId, String type, String account, String intentId, String paymentRef, String status, long amount,
            String failureCode, String failureMessage) {
        return event(eventId, type, account, "{\"object\":\"payment_intent\",\"id\":\"" + intentId + "\",\"status\":\"" + status + "\",\"amount\":" + amount
                + ",\"currency\":\"usd\"" + (paymentRef == null ? "" : ",\"metadata\":{\"billing_payment_id\":\"" + paymentRef + "\"}")
                + (failureCode == null ? "" : ",\"last_payment_error\":{\"code\":\"card_declined\",\"decline_code\":\"" + failureCode + "\",\"message\":\""
                        + failureMessage + "\"}") + "}");
    }

    public static String refund(String eventId, String account, String refundId, String intentId, String refundRef, String status, long amount) {
        return event(eventId, "refund.updated", account, "{\"object\":\"refund\",\"id\":\"" + refundId + "\",\"status\":\"" + status + "\",\"amount\":"
                + amount + ",\"currency\":\"usd\",\"payment_intent\":\"" + intentId + "\""
                + (refundRef == null ? "" : ",\"metadata\":{\"billing_refund_id\":\"" + refundRef + "\"}") + "}");
    }

    /** The {@code Stripe-Signature} header for {@code body}: {@code t=<ts>,v1=hex(hmac_sha256(secret, ts + "." + body))}. */
    public static String sign(String secret, String body) {
        return sign(secret, body, Instant.now().getEpochSecond());
    }

    public static String sign(String secret, String body, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
