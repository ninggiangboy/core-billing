package dev.ngb.core_billing.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.payment.PaymentTestWorld;
import dev.ngb.core_billing.payment.PaymentTestWorld.PayWorld;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.support.FakeIdentityConfig;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.StripeEvents;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Stripe's webhook endpoints over HTTP with the real Stripe adapter: no login, the signature is the proof, and
 * what a verified event does to the books. Signatures are computed here the way Stripe computes them.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, FakeIdentityConfig.class})
@TestPropertySource(properties = {
    "billing.payments.connect.platform-secret-key=sk_test_platform_key_for_tests_only",
    "billing.payments.connect.webhook-secret=whsec_connect_for_tests_only_0123456789"})
class StripeWebhookHttpIntegrationTest {

    private static final String CONNECT_SECRET = "whsec_connect_for_tests_only_0123456789";

    @Autowired MockMvc mvc;
    @Autowired PaymentTestWorld worlds;
    @Autowired OwnerDatabase owner;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private String host() {
        return "platform." + baseDomain;
    }

    private MvcResult send(String path, String body, String signature) throws Exception {
        var request = post(path).header("Host", host()).contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        return mvc.perform(request).andReturn();
    }

    private static String direct(PayWorld w) {
        return "/stripe/webhooks/direct/" + w.configuration().id();
    }

    private Payment open(PayWorld w, long amount) {
        return worlds.openPayment(w, new PaymentId(UUID.randomUUID()), amount);
    }

    private long balance(PayWorld w) {
        return owner.count("select balance from wallets where id = ?", w.money().wallet().id().value());
    }

    private String paid(Payment p, String account) {
        return StripeEvents.paidCheckout(StripeEvents.newEventId(), account, "cs_" + p.id(), "pi_" + p.id(), p.id().toString(), p.amount().minorUnits());
    }

    private long incidents(PayWorld w, String kind) {
        return owner.count("select coalesce(sum(occurrences), 0) from stripe_incidents where tenant_id = ? and kind = ?", w.money().tenant().id().value(), kind);
    }

    @Test
    void aSignedEventNeedsNoLoginAndCreditsTheWalletOnce() throws Exception {
        PayWorld w = worlds.direct(0);
        Payment p = open(w, 5000);
        String body = paid(p, null);
        String signature = StripeEvents.sign(w.webhookSecret(), body);

        MvcResult first = send(direct(w), body, signature);
        MvcResult replay = send(direct(w), body, signature);

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(first.getResponse().getContentAsString()).contains("processed");
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getContentAsString()).contains("duplicate");
        assertThat(balance(w)).isEqualTo(5000);
        assertThat(owner.count("select count(*) from ledger_entries where wallet_id = ?", w.money().wallet().id().value())).isEqualTo(1);
    }

    @Test
    void aWrongSecretIsRefusedRecordedAndChangesNothing() throws Exception {
        PayWorld w = worlds.direct(0);
        Payment p = open(w, 5000);
        String body = paid(p, null);
        String badSignature = StripeEvents.sign("whsec_somebody_elses_secret", body);

        MvcResult result = send(direct(w), body, badSignature);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(w.webhookSecret()).doesNotContain("whsec_").doesNotContain(badSignature);
        assertThat(balance(w)).isZero();
        assertThat(incidents(w, "SIGNATURE_REJECTED")).isEqualTo(1);
        assertThat(owner.count("select count(*) from stripe_events where tenant_id = ?", w.money().tenant().id().value())).isZero();
    }

    @Test
    void aStaleTimestampATamperedBodyAndAMissingHeaderAreAllRefused() throws Exception {
        PayWorld w = worlds.direct(0);
        Payment p = open(w, 5000);
        String body = paid(p, null);

        assertThat(send(direct(w), body, StripeEvents.sign(w.webhookSecret(), body, Instant.now().getEpochSecond() - 3600)).getResponse().getStatus())
                .as("stale").isEqualTo(400);
        assertThat(send(direct(w), body.replace("\"amount_total\":5000", "\"amount_total\":1"), StripeEvents.sign(w.webhookSecret(), body)).getResponse()
                .getStatus()).as("tampered").isEqualTo(400);
        assertThat(send(direct(w), body, null).getResponse().getStatus()).as("missing header").isEqualTo(400);
        assertThat(balance(w)).isZero();
        assertThat(incidents(w, "SIGNATURE_REJECTED")).isEqualTo(3);
    }

    @Test
    void anOversizedBodyIsRefusedBeforeAnyWorkIsDone() throws Exception {
        PayWorld w = worlds.direct(0);
        String huge = "{\"id\":\"evt_big\",\"pad\":\"" + "x".repeat(600 * 1024) + "\"}";

        assertThat(send(direct(w), huge, StripeEvents.sign(w.webhookSecret(), huge)).getResponse().getStatus()).isEqualTo(400);
        assertThat(owner.count("select count(*) from stripe_incidents where tenant_id = ?", w.money().tenant().id().value())).as("nothing was even verified").isZero();
    }

    @Test
    void anUnknownEndpointIsNotFound() throws Exception {
        String body = StripeEvents.paidCheckout(StripeEvents.newEventId(), null, "cs_x", "pi_x", UUID.randomUUID().toString(), 1);
        assertThat(send("/stripe/webhooks/direct/" + UUID.randomUUID(), body, StripeEvents.sign("whsec_x", body)).getResponse().getStatus()).isEqualTo(404);
        assertThat(send("/stripe/webhooks/direct/not-a-uuid", body, StripeEvents.sign("whsec_x", body)).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void anEndpointWithoutAWebhookSecretCannotVerifyAnything() throws Exception {
        PayWorld w = worlds.direct(0);
        owner.jdbc().update("update stripe_configurations set webhook_secret_ciphertext = null, status = 'PENDING' where id = ?",
                w.configuration().id().value());
        String body = paid(open(w, 100), null);

        assertThat(send(direct(w), body, StripeEvents.sign("whsec_anything", body)).getResponse().getStatus()).isEqualTo(400);
        assertThat(balance(w)).isZero();
    }

    @Test
    void aBrowserRedirectToTheSuccessPageChangesNothing() throws Exception {
        PayWorld w = worlds.direct(0);
        Payment p = open(w, 5000);

        // The customer is sent back from Stripe's hosted page; whatever that request is, it is not a payment result.
        int status = mvc.perform(get("/top-up/done").param("payment", p.id().toString()).header("Host", w.money().tenant().code() + "." + baseDomain))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 404);
        assertThat(balance(w)).isZero();
        assertThat(owner.count("select count(*) from ledger_entries where wallet_id = ?", w.money().wallet().id().value())).isZero();
        assertThat(owner.count("select count(*) from payments where id = ? and status = 'REQUIRES_ACTION'", p.id().value())).isEqualTo(1);
    }

    @Test
    void theApiAroundTheWebhooksStillNeedsALogin() throws Exception {
        assertThat(mvc.perform(get("/api/v1/payments").header("Host", host())).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void theConnectEndpointVerifiesWithThePlatformSecretAndRoutesByAccount() throws Exception {
        PayWorld w = worlds.connected(0);
        Payment p = open(w, 7000);
        String body = paid(p, w.configuration().accountId());

        assertThat(send("/stripe/webhooks/connect", body, StripeEvents.sign("whsec_a_tenants_direct_secret", body)).getResponse().getStatus())
                .as("not the platform's secret").isEqualTo(400);
        assertThat(balance(w)).isZero();

        MvcResult accepted = send("/stripe/webhooks/connect", body, StripeEvents.sign(CONNECT_SECRET, body));

        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
        assertThat(accepted.getResponse().getContentAsString()).contains("processed");
        assertThat(balance(w)).isEqualTo(7000);
        assertThat(owner.count("select count(*) from stripe_incidents where tenant_id = ? and kind = 'SIGNATURE_REJECTED'", RequestScope.PLATFORM_TENANT_ID.value()))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void aConnectEventForAnAccountNoTenantHasLinkedIsIgnoredAndFlagged() throws Exception {
        String body = StripeEvents.paidCheckout(StripeEvents.newEventId(), "acct_nobody_" + UUID.randomUUID().toString().substring(0, 8), "cs_x", "pi_x",
                UUID.randomUUID().toString(), 1);

        MvcResult result = send("/stripe/webhooks/connect", body, StripeEvents.sign(CONNECT_SECRET, body));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("ignored");
        assertThat(owner.count("select count(*) from stripe_incidents where tenant_id = ? and kind = 'UNKNOWN_ACCOUNT'", RequestScope.PLATFORM_TENANT_ID.value()))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void aConnectEventNamingTheAccountOfAnotherTenantCannotCreditAnothersWallet() throws Exception {
        PayWorld a = worlds.connected(0);
        PayWorld b = worlds.connected(0);
        Payment bPayment = open(b, 7000);
        // Genuinely signed by Stripe for account A, but naming B's payment.
        String body = paid(bPayment, a.configuration().accountId());

        send("/stripe/webhooks/connect", body, StripeEvents.sign(CONNECT_SECRET, body));

        assertThat(balance(b)).isZero();
        assertThat(balance(a)).isZero();
    }

    @Test
    void aDirectEndpointRefusesAnEventForADifferentAccount() throws Exception {
        PayWorld w = worlds.direct(0);
        Payment p = open(w, 5000);
        String body = paid(p, "acct_some_other_account");

        MvcResult result = send(direct(w), body, StripeEvents.sign(w.webhookSecret(), body));

        assertThat(result.getResponse().getContentAsString()).contains("ignored");
        assertThat(balance(w)).isZero();
        assertThat(incidents(w, "EVENT_REJECTED")).isEqualTo(1);
    }
}
