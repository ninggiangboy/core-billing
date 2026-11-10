package io.corebilling.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.payment.adapter.stripe.StripePaymentProvider;
import io.corebilling.payment.application.InvalidSignatureException;
import io.corebilling.payment.application.PaymentProvider.ProviderEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Signature verification with the real SDK and signatures computed here the way Stripe documents them
 * ({@code t=<ts>,v1=hex(hmac_sha256(secret, ts + "." + body))}). The same check serves a Direct endpoint (the
 * configuration's own secret) and the Connect endpoint (the Platform's), so both secrets are exercised.
 */
class StripeWebhookSignatureTest {

    private static final String DIRECT_SECRET = "whsec_direct_secret_value";
    private static final String CONNECT_SECRET = "whsec_connect_secret_value";
    private static final String BODY = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\",\"livemode\":false,\"created\":1789000000,"
            + "\"data\":{\"object\":{\"object\":\"payment_intent\",\"id\":\"pi_1\",\"status\":\"succeeded\",\"amount\":100,\"currency\":\"usd\"}}}";

    private final StripePaymentProvider provider = new StripePaymentProvider(null, Duration.ofSeconds(1), Duration.ofSeconds(1), 300);

    private static String sign(String secret, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String digest = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + digest;
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    @Test
    void aValidSignatureVerifiesForBothSecrets() throws Exception {
        for (String secret : new String[] {DIRECT_SECRET, CONNECT_SECRET}) {
            ProviderEvent event = provider.verifyWebhook(BODY, sign(secret, now(), BODY), secret);
            assertThat(event.id()).isEqualTo("evt_1");
            assertThat(event.type()).isEqualTo("payment_intent.succeeded");
        }
    }

    @Test
    void theWrongSecretIsRejected() throws Exception {
        String header = sign(DIRECT_SECRET, now(), BODY);
        assertThatThrownBy(() -> provider.verifyWebhook(BODY, header, CONNECT_SECRET)).isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void aStaleTimestampIsRejectedEvenWithAValidMac() throws Exception {
        String header = sign(DIRECT_SECRET, now() - 3600, BODY);
        assertThatThrownBy(() -> provider.verifyWebhook(BODY, header, DIRECT_SECRET)).isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void aTamperedBodyIsRejected() throws Exception {
        String header = sign(DIRECT_SECRET, now(), BODY);
        String tampered = BODY.replace("\"amount\":100", "\"amount\":999999");
        assertThatThrownBy(() -> provider.verifyWebhook(tampered, header, DIRECT_SECRET)).isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void aMissingOrMalformedHeaderIsRejected() {
        assertThatThrownBy(() -> provider.verifyWebhook(BODY, null, DIRECT_SECRET)).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> provider.verifyWebhook(BODY, "", DIRECT_SECRET)).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> provider.verifyWebhook(BODY, "garbage", DIRECT_SECRET)).isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void theFailureNeverRevealsTheSecretOrTheExpectedSignature() throws Exception {
        String header = sign(DIRECT_SECRET, now(), BODY);
        assertThatThrownBy(() -> provider.verifyWebhook(BODY, header, CONNECT_SECRET))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CONNECT_SECRET).doesNotContain(DIRECT_SECRET).doesNotContain(header));
    }
}
