package io.corebilling.webhook.application;

public interface PayloadSigner {

    /** @return the {@code Billing-Signature} header value for the body signed at {@code timestampSeconds} */
    String sign(String secret, long timestampSeconds, byte[] body);
}
