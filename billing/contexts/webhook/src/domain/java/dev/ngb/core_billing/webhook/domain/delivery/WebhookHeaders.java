package dev.ngb.core_billing.webhook.domain.delivery;

/**
 * The headers of a delivery. The signature is {@code v1=} followed by the lowercase hex HMAC-SHA256 of
 * {@code timestamp + "." + rawBody}, keyed with the endpoint's signing secret string.
 */
public final class WebhookHeaders {

    public static final String EVENT_ID = "Billing-Event-Id";
    public static final String EVENT_TYPE = "Billing-Event-Type";
    public static final String TIMESTAMP = "Billing-Timestamp";
    public static final String KEY_ID = "Billing-Key-Id";
    public static final String SIGNATURE = "Billing-Signature";
    public static final String ATTEMPT = "Billing-Attempt";
    public static final String SIGNATURE_VERSION = "v1";

    private WebhookHeaders() {}
}
