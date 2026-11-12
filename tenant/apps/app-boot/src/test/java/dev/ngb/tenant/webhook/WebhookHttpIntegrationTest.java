package dev.ngb.tenant.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.tenant.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "tenant.webhooks.signing-secret=whsec_test")
class WebhookHttpIntegrationTest extends HttpIntegrationTest {

    private static final String SECRET = "whsec_test";

    @Test
    void acceptsAFreshSignedDeliveryAndDeduplicatesIt() {
        String body = paymentFailed();
        String eventId = eventId(body);
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        assertThat(status(post(eventId, "payment.failed", timestamp, sign(SECRET, timestamp, body), body))).isEqualTo(200);
        assertThat(status(post(eventId, "payment.failed", timestamp, sign(SECRET, timestamp, body), body))).isEqualTo(200);
    }

    @Test
    void rejectsAForgedSignature() {
        String body = paymentFailed();
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        assertThat(status(post(eventId(body), "payment.failed", timestamp, "v1=deadbeef", body))).isEqualTo(401);
    }

    @Test
    void rejectsAStaleTimestamp() {
        String body = paymentFailed();
        String timestamp = Long.toString(Instant.now().minusSeconds(600).getEpochSecond());

        assertThat(status(post(eventId(body), "payment.failed", timestamp, sign(SECRET, timestamp, body), body))).isEqualTo(401);
    }

    private int status(org.springframework.test.web.servlet.MvcResult result) {
        return result.getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.MvcResult post(
            String eventId, String eventType, String timestamp, String signature, String body) {
        return world.callRaw(HttpMethod.POST, null, "/webhooks/billing", body, Map.of(
                "Billing-Event-Id", eventId,
                "Billing-Event-Type", eventType,
                "Billing-Timestamp", timestamp,
                "Billing-Signature", signature));
    }

    private static String paymentFailed() {
        return "{\"id\":\"" + "evt_" + UUID.randomUUID()
                + "\",\"type\":\"payment.failed\",\"tenantId\":\"tenant-1\",\"occurredAt\":\"2026-11-11T00:00:00Z\","
                + "\"data\":{\"customerId\":\"cus_1\",\"amount\":{\"minorUnits\":500,\"currency\":\"USD\"},\"reason\":\"declined\"}}";
    }

    private static String eventId(String body) {
        int start = body.indexOf("\"id\":\"") + "\"id\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    private static String sign(String secret, String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body.getBytes(StandardCharsets.UTF_8));
            return "v1=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
