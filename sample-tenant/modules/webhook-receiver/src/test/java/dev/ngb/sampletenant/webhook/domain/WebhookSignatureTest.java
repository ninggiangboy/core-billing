package dev.ngb.sampletenant.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private static final String SECRET = "whsec_test";
    private static final String TIMESTAMP = "1762819200";
    private static final byte[] BODY = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsAFreshCorrectSignature() {
        assertThat(WebhookSignature.verify(SECRET, TIMESTAMP, BODY, sign(SECRET, TIMESTAMP, BODY), 1762819200L, 300))
                .isTrue();
    }

    @Test
    void rejectsAForgedSignature() {
        assertThat(WebhookSignature.verify(SECRET, TIMESTAMP, BODY, "v1=deadbeef", 1762819200L, 300))
                .isFalse();
    }

    @Test
    void rejectsAStaleTimestamp() {
        assertThat(WebhookSignature.verify(SECRET, TIMESTAMP, BODY, sign(SECRET, TIMESTAMP, BODY), 1762819200L + 301, 300))
                .isFalse();
    }

    @Test
    void rejectsAFutureTimestamp() {
        assertThat(WebhookSignature.verify(SECRET, TIMESTAMP, BODY, sign(SECRET, TIMESTAMP, BODY), 1762819200L - 301, 300))
                .isFalse();
    }

    @Test
    void rejectsAMissingSecret() {
        assertThat(WebhookSignature.verify("", TIMESTAMP, BODY, sign(SECRET, TIMESTAMP, BODY), 1762819200L, 300))
                .isFalse();
    }

    static String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return "v1=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
