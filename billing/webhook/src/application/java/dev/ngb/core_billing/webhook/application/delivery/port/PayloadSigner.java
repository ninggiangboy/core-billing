package dev.ngb.core_billing.webhook.application.delivery.port;

public interface PayloadSigner {

    /** @return the {@code Billing-Signature} header value for the body signed at {@code timestampSeconds} */
    String sign(String secret, long timestampSeconds, byte[] body);
}
