package dev.ngb.core_billing.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.support.FakePaymentProvider;
import dev.ngb.core_billing.support.FakePaymentProviderConfig;
import dev.ngb.core_billing.support.HttpWorld;
import dev.ngb.core_billing.support.HttpWorld.Account;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RealKeycloakConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.StripeEvents;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Billing Session and Customer Portal over HTTP: a session is issued to a Tenant System, exchanged once for
 * an HttpOnly cookie, and can never reach another customer or tenant. State-changing calls need the CSRF token,
 * a narrowed session gets 403 on the endpoints it excludes, and a portal top-up credits the wallet only when the
 * verified Stripe event arrives.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class, FakePaymentProviderConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalHttpIntegrationTest {

    private static final String SECRET_KEY = "sk_test_portal_secret_key_ab12cd34";
    private static final String WEBHOOK_SECRET = "whsec_portal_webhook_secret_0123456789";
    private static final String RETURN_URL = "https://app.acme.test/portal-done";

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired FakePaymentProvider stripe;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private final JsonMapper json = JsonMapper.builder().build();
    HttpWorld world;
    Account acme;
    Account globex;
    String systemKey;
    String customerA;
    String customerB;
    String walletA;
    String walletB;
    String configId;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        globex = world.newTenant();
        String systemId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems",
                HttpWorld.Map2.of("name", "Portal System"))).get("id").asString();
        systemKey = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials", HttpWorld.Map2.of("label", "portal", "scopes", List.of(
                        "billing-session:create", "customer:read", "customer:write", "wallet:read", "wallet:manage",
                        "payment:create", "payment:read", "charge:create", "usage:record", "usage:read", "invoice:read",
                        "invoice:create", "subscription:manage", "subscription:read")))).get("key").asString();
        customerA = createCustomer("a-" + UUID.randomUUID());
        customerB = createCustomer("b-" + UUID.randomUUID());
        walletA = openWallet(customerA, "USD");
        walletB = openWallet(customerB, "USD");

        assertThat(world.call(HttpMethod.PUT, acme.host(), acme.adminToken(), "/api/v1/portal/return-urls",
                HttpWorld.Map2.of("urls", List.of(RETURN_URL))).getResponse().getStatus()).isEqualTo(200);
        configId = world.body(world.call(HttpMethod.PUT, acme.host(), acme.adminToken(), "/api/v1/stripe/configuration/direct",
                HttpWorld.Map2.of("secretKey", SECRET_KEY, "publishableKey", "pk_test_portal", "webhookSecret", WEBHOOK_SECRET))).get("id").asString();
    }

    @Test
    void aSessionIsBoundToItsCustomerAndTenant() throws Exception {
        Session session = createSession(customerA, null);
        MvcResult exchanged = exchange(acme.host(), session.exchangeToken);
        assertThat(exchanged.getResponse().getStatus()).isEqualTo(200);
        JsonNode exchangedBody = json.readTree(exchanged.getResponse().getContentAsString());
        assertThat(exchangedBody.get("returnUrl").asString()).isEqualTo(RETURN_URL);
        String setCookies = String.join("\n", exchanged.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
        assertThat(setCookies).contains("billing_portal=").contains("HttpOnly").contains("Secure").contains("SameSite=Lax");
        assertThat(setCookies).contains("billing_portal_csrf=");
        Portal portal = portal(acme.host(), exchanged);

        assertThat(portal.status(HttpMethod.GET, "/portal/api/customer", null)).isEqualTo(200);
        assertThat(portal.body("/portal/api/customer").get("id").asString()).isEqualTo(customerA);

        JsonNode sessionBody = portal.body("/portal/api/session");
        assertThat(sessionBody.get("customerId").asString()).isEqualTo(customerA);
        assertThat(sessionBody.get("permissions").toString()).contains("customer:view").contains("wallet:view");

        JsonNode wallets = portal.body("/portal/api/wallets");
        assertThat(wallets.size()).isEqualTo(1);
        assertThat(wallets.get(0).get("id").asString()).isEqualTo(walletA);
        assertThat(portal.status(HttpMethod.GET, "/portal/api/wallets/" + walletB, null)).isEqualTo(404);
        assertThat(portal.status(HttpMethod.GET, "/portal/api/wallets/" + walletA + "/ledger", null)).isEqualTo(200);
        assertThat(portal.status(HttpMethod.GET, "/portal/api/payments", null)).isEqualTo(200);
        assertThat(portal.status(HttpMethod.GET, "/portal/api/invoices", null)).isEqualTo(200);
        assertThat(portal.status(HttpMethod.GET, "/portal/api/subscriptions", null)).isEqualTo(200);

        // A token issued for acme is simply unknown on globex's hostname.
        assertThat(exchange(globex.host(), session.exchangeToken).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anExchangeTokenWorksOnlyOnce() throws Exception {
        Session session = createSession(customerA, null);

        assertThat(exchange(acme.host(), session.exchangeToken).getResponse().getStatus()).isEqualTo(200);
        assertThat(exchange(acme.host(), session.exchangeToken).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void revokedAndExpiredSessionsFailImmediately() throws Exception {
        Session revoked = createSession(customerA, null);
        Portal revokedPortal = portal(acme.host(), exchange(acme.host(), revoked.exchangeToken));
        assertThat(world.call(HttpMethod.POST, acme.host(), systemKey, "/api/v1/billing-sessions/" + revoked.id + "/revoke", null)
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(revokedPortal.status(HttpMethod.GET, "/portal/api/customer", null)).isEqualTo(401);

        Session expired = createSession(customerA, null);
        owner.jdbc().update("update billing_sessions set created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour' where id = ?",
                UUID.fromString(expired.id));
        assertThat(exchange(acme.host(), expired.exchangeToken).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aNarrowedSessionIsForbiddenOnTheEndpointsItExcludes() throws Exception {
        Portal portal = portal(acme.host(), exchange(acme.host(), createSession(customerA, List.of("customer:view")).exchangeToken));

        assertThat(portal.status(HttpMethod.GET, "/portal/api/customer", null)).isEqualTo(200);
        assertThat(portal.status(HttpMethod.GET, "/portal/api/wallets", null)).isEqualTo(403);
        assertThat(portal.status(HttpMethod.POST, "/portal/api/top-ups", HttpWorld.Map2.of("walletId", walletA, "amount", 100, "currency", "USD")))
                .isEqualTo(403);
    }

    @Test
    void aReturnUrlOffTheAllowlistIsRejected() {
        MvcResult result = world.call(HttpMethod.POST, acme.host(), systemKey, "/api/v1/billing-sessions", HttpWorld.Map2.of(
                "customerId", customerA, "returnUrl", "https://evil.example.com/done"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void stateChangingPortalCallsNeedTheCsrfToken() throws Exception {
        Portal portal = portal(acme.host(), exchange(acme.host(), createSession(customerA, List.of("top-up:create", "wallet:view")).exchangeToken));

        MvcResult withoutToken = portal.call(HttpMethod.POST, "/portal/api/top-ups", HttpWorld.Map2.of(
                "walletId", walletA, "amount", 500, "currency", "USD"), false);
        assertThat(withoutToken.getResponse().getStatus()).isEqualTo(403);
        assertThat(withoutToken.getResponse().getContentAsString()).contains("csrf");

        MvcResult withToken = portal.call(HttpMethod.POST, "/portal/api/top-ups", HttpWorld.Map2.of(
                "walletId", walletA, "amount", 500, "currency", "USD"), true);
        assertThat(withToken.getResponse().getStatus()).isEqualTo(201);
        assertThat(json.readTree(withToken.getResponse().getContentAsString()).get("checkoutUrl").asString())
                .startsWith("https://checkout.stripe.test/");
    }

    @Test
    void aPortalTopUpCreditsTheWalletOnlyAfterTheVerifiedEvent() throws Exception {
        Portal portal = portal(acme.host(), exchange(acme.host(), createSession(customerA, List.of("top-up:create", "wallet:view")).exchangeToken));
        long before = portal.body("/portal/api/wallets/" + walletA).get("balance").get("amount").asLong();

        MvcResult created = portal.call(HttpMethod.POST, "/portal/api/top-ups", HttpWorld.Map2.of(
                "walletId", walletA, "amount", 2500, "currency", "USD"), true);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String paymentId = json.readTree(created.getResponse().getContentAsString()).get("id").asString();
        assertThat(portal.body("/portal/api/wallets/" + walletA).get("balance").get("amount").asLong()).isEqualTo(before);

        String sessionId = owner.jdbc().queryForObject("select checkout_session_id from payments where id = ?", String.class, UUID.fromString(paymentId));
        String body = StripeEvents.paidCheckout(StripeEvents.newEventId(), null, sessionId, "pi_portal_" + UUID.randomUUID().toString().substring(0, 8),
                paymentId, 2500);
        MvcResult delivered = mvc.perform(post("/stripe/webhooks/direct/" + configId).header("Host", "platform." + baseDomain)
                .contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8))
                .header("Stripe-Signature", StripeEvents.sign(WEBHOOK_SECRET, body))).andReturn();
        assertThat(delivered.getResponse().getStatus()).isEqualTo(200);

        assertThat(portal.body("/portal/api/wallets/" + walletA).get("balance").get("amount").asLong()).isEqualTo(before + 2500);
    }

    @Test
    void paymentMethodsOpenTheHostedPageOncePerCustomer() throws Exception {
        Portal portal = portal(acme.host(), exchange(acme.host(), createSession(customerA, List.of("payment-method:manage")).exchangeToken));

        MvcResult first = portal.call(HttpMethod.POST, "/portal/api/payment-methods", null, true);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(first.getResponse().getContentAsString()).get("url").asString()).startsWith("https://billing.stripe.test/session/");
        int customersAfterFirst = stripe.customersByKey.size();
        assertThat(customersAfterFirst).as("a provider customer was created").isGreaterThanOrEqualTo(1);
        MvcResult second = portal.call(HttpMethod.POST, "/portal/api/payment-methods", null, true);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(stripe.customersByKey).as("the provider customer is reused").hasSize(customersAfterFirst);
    }

    @Test
    void creatingASessionRequiresTheSystemPermission() {
        assertThat(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/billing-sessions", HttpWorld.Map2.of(
                "customerId", customerA, "returnUrl", RETURN_URL)).getResponse().getStatus())
                .as("a tenant user is not a tenant system").isEqualTo(403);
    }

    private String createCustomer(String externalUserId) {
        try {
            MvcResult result = mvc.perform(post("/api/v1/customers").header("Host", acme.host())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemKey)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(HttpWorld.Map2.of("externalUserId", externalUserId, "displayName", "Customer " + externalUserId))))
                    .andReturn();
            return world.body(result).get("id").asString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String openWallet(String customerId, String currency) {
        return world.body(world.call(HttpMethod.POST, acme.host(), systemKey, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", customerId, "currency", currency, "mode", "PREPAID"))).get("id").asString();
    }

    private Session createSession(String customerId, List<String> permissions) {
        MvcResult result = world.call(HttpMethod.POST, acme.host(), systemKey, "/api/v1/billing-sessions", HttpWorld.Map2.of(
                "customerId", customerId, "returnUrl", RETURN_URL, "permissions", permissions));
        JsonNode body = world.body(result);
        String portalUrl = body.get("portalUrl").asString();
        return new Session(body.get("id").asString(), portalUrl.substring(portalUrl.indexOf("#token=") + "#token=".length()));
    }

    private MvcResult exchange(String host, String token) throws Exception {
        return mvc.perform(post("/portal/api/session/exchange").header("Host", host)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(HttpWorld.Map2.of("token", token)))).andReturn();
    }

    private Portal portal(String host, MvcResult exchanged) throws Exception {
        return new Portal(host, cookie(exchanged, "billing_portal"), cookie(exchanged, "billing_portal_csrf"));
    }

    private static String cookie(MvcResult result, String name) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(name + "=")) {
                return header.substring(name.length() + 1, header.indexOf(';'));
            }
        }
        throw new IllegalStateException("missing cookie " + name + " in " + result.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
    }

    private record Session(String id, String exchangeToken) {}

    private final class Portal {
        private final String host;
        private final String session;
        private final String csrf;

        private Portal(String host, String session, String csrf) {
            this.host = host;
            this.session = session;
            this.csrf = csrf;
        }

        int status(HttpMethod method, String path, Object payload) {
            return call(method, path, payload, true).getResponse().getStatus();
        }

        JsonNode body(String path) {
            return world.body(call(HttpMethod.GET, path, null, true));
        }

        MvcResult call(HttpMethod method, String path, Object payload, boolean withCsrf) {
            try {
                var builder = request(method, path).header("Host", host).cookie(new Cookie("billing_portal", session));
                if (withCsrf) {
                    builder.cookie(new Cookie("billing_portal_csrf", csrf)).header("X-Portal-CSRF", csrf);
                }
                if (payload != null) {
                    builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
                }
                if (method == HttpMethod.POST && path.equals("/portal/api/top-ups")) {
                    builder.header("Idempotency-Key", "portal-" + UUID.randomUUID());
                }
                return mvc.perform(builder).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
