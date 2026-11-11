package dev.ngb.core_billing.payment.adapter.stripe.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider.CheckoutPayment;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider.CheckoutStatus;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider.IntentState;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider.ProviderEvent;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider.RefundState;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider.Subject;
import java.time.Instant;

/**
 * Reads Stripe's JSON into the few facts Billing decides on. It works on the raw JSON on purpose: an event is
 * rendered in the API version of the webhook endpoint that received it, which need not be the version the SDK
 * is pinned to, and the fields used here are the stable ones. Everything else in the object is ignored.
 */
final class StripeSubjects {

    /** Metadata keys Billing sets on the Stripe objects it creates, so an event can be tied back to a local record. */
    static final String PAYMENT_REF = "billing_payment_id";
    static final String REFUND_REF = "billing_refund_id";
    static final String TENANT_REF = "billing_tenant_id";

    private StripeSubjects() {}

    static ProviderEvent event(String rawEvent) {
        JsonObject event = JsonParser.parseString(rawEvent).getAsJsonObject();
        JsonObject object = event.has("data") && event.getAsJsonObject("data").has("object") ? event.getAsJsonObject("data").getAsJsonObject("object") : null;
        return new ProviderEvent(
                text(event, "id"),
                text(event, "type"),
                text(event, "account"),
                event.has("livemode") && event.get("livemode").getAsBoolean(),
                event.has("created") && !event.get("created").isJsonNull() ? Instant.ofEpochSecond(event.get("created").getAsLong()) : Instant.EPOCH,
                object == null ? new PaymentProvider.Unhandled() : subject(object));
    }

    static Subject subject(String rawObject) {
        return subject(JsonParser.parseString(rawObject).getAsJsonObject());
    }

    static Subject subject(JsonObject object) {
        String kind = text(object, "object");
        if (kind == null) {
            return new PaymentProvider.Unhandled();
        }
        return switch (kind) {
            case "checkout.session" -> checkout(object);
            case "payment_intent" -> intent(object);
            case "refund" -> refund(object);
            case "account" -> new PaymentProvider.AccountObject(text(object, "id"));
            default -> new PaymentProvider.Unhandled();
        };
    }

    private static PaymentProvider.Checkout checkout(JsonObject o) {
        String ref = metadata(o, PAYMENT_REF);
        if (ref == null) {
            ref = text(o, "client_reference_id");
        }
        return new PaymentProvider.Checkout(
                text(o, "id"),
                reference(o, "payment_intent"),
                ref,
                switch (String.valueOf(text(o, "status"))) {
                    case "complete" -> CheckoutStatus.COMPLETE;
                    case "expired" -> CheckoutStatus.EXPIRED;
                    default -> CheckoutStatus.OPEN;
                },
                switch (String.valueOf(text(o, "payment_status"))) {
                    case "paid" -> CheckoutPayment.PAID;
                    case "no_payment_required" -> CheckoutPayment.NO_PAYMENT_REQUIRED;
                    default -> CheckoutPayment.UNPAID;
                },
                number(o, "amount_total"),
                text(o, "currency"));
    }

    private static PaymentProvider.Intent intent(JsonObject o) {
        JsonObject error = o.has("last_payment_error") && o.get("last_payment_error").isJsonObject() ? o.getAsJsonObject("last_payment_error") : null;
        return new PaymentProvider.Intent(
                text(o, "id"),
                metadata(o, PAYMENT_REF),
                switch (String.valueOf(text(o, "status"))) {
                    case "succeeded" -> IntentState.SUCCEEDED;
                    case "processing" -> IntentState.PROCESSING;
                    case "requires_payment_method" -> IntentState.REQUIRES_PAYMENT_METHOD;
                    case "requires_action", "requires_confirmation", "requires_capture" -> IntentState.REQUIRES_ACTION;
                    case "canceled" -> IntentState.CANCELED;
                    default -> IntentState.OTHER;
                },
                number(o, "amount"),
                text(o, "currency"),
                error == null ? null : firstNonNull(text(error, "decline_code"), text(error, "code")),
                error == null ? null : text(error, "message"));
    }

    private static PaymentProvider.RefundObject refund(JsonObject o) {
        return new PaymentProvider.RefundObject(
                text(o, "id"),
                reference(o, "payment_intent"),
                metadata(o, REFUND_REF),
                switch (String.valueOf(text(o, "status"))) {
                    case "succeeded" -> RefundState.SUCCEEDED;
                    case "failed" -> RefundState.FAILED;
                    case "canceled" -> RefundState.CANCELED;
                    default -> RefundState.PENDING;
                },
                number(o, "amount"),
                text(o, "currency"),
                text(o, "failure_reason"));
    }

    /** An id that Stripe may send as a string or, when expanded, as an object with an {@code id}. */
    private static String reference(JsonObject o, String field) {
        JsonElement element = o.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.isJsonObject() ? text(element.getAsJsonObject(), "id") : element.getAsString();
    }

    private static String metadata(JsonObject o, String key) {
        JsonElement metadata = o.get("metadata");
        return metadata != null && metadata.isJsonObject() ? text(metadata.getAsJsonObject(), key) : null;
    }

    private static String text(JsonObject o, String field) {
        JsonElement element = o.get(field);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static long number(JsonObject o, String field) {
        JsonElement element = o.get(field);
        return element == null || element.isJsonNull() ? 0 : element.getAsLong();
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
