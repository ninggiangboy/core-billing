package io.corebilling.webhook.adapter.crypto;

import io.corebilling.webhook.application.delivery.port.PayloadSigner;
import io.corebilling.webhook.domain.delivery.WebhookHeaders;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** {@code v1=hex(HMAC-SHA256(secret, timestamp + "." + rawBody))}, the format receivers verify. */
public final class HmacSha256Signer implements PayloadSigner {

    private static final String ALGORITHM = "HmacSHA256";

    @Override
    public String sign(String secret, long timestampSeconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update((timestampSeconds + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return WebhookHeaders.SIGNATURE_VERSION + "=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is required by the Java platform", e);
        }
    }
}
