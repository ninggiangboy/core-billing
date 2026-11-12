package dev.ngb.tenant.webhook.domain.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Verifies Billing's signed delivery over the raw body, with a stale-timestamp bound. */
public final class WebhookSignature {

    private WebhookSignature() {}

    public static boolean verify(
            String secret, String timestamp, byte[] rawBody, String signatureHeader, long nowSeconds, long toleranceSeconds) {
        if (secret == null || secret.isBlank() || timestamp == null || signatureHeader == null) {
            return false;
        }
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(nowSeconds - timestampSeconds) > toleranceSeconds) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(rawBody);
            String expected = "v1=" + HexFormat.of().formatHex(mac.doFinal());
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is required by the JDK", e);
        }
    }
}
