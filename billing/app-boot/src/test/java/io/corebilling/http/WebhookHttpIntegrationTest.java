package io.corebilling.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.corebilling.Application;
import io.corebilling.boot.infrastructure.outbox.OutboxRelay;
import io.corebilling.platform.application.BootstrapPlatformAdminUseCase;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.support.EventSchemas;
import io.corebilling.support.HttpWorld;
import io.corebilling.support.HttpWorld.Account;
import io.corebilling.support.PostgresContainerConfig;
import io.corebilling.support.RealKeycloakConfig;
import io.corebilling.support.ScopeTestSupport;
import io.corebilling.support.TestReceiver;
import io.corebilling.webhook.application.delivery.command.DeliverWebhooksUseCase;
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
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** The webhook API over HTTP: permissions, the one-time secret, validation, isolation and the delivery history. */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebhookHttpIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Autowired OutboxRelay relay;
    @Autowired DeliverWebhooksUseCase deliver;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    HttpWorld world;
    Account acme;
    Account globex;
    String manageKey;
    String readKey;
    String noWebhookKey;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        globex = world.newTenant();
        String systemId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems", HttpWorld.Map2.of("name", "Sample")))
                .get("id").asString();
        manageKey = credential(systemId, List.of("webhook:manage", "webhook:read", "customer:write", "customer:read"));
        readKey = credential(systemId, List.of("webhook:read"));
        noWebhookKey = credential(systemId, List.of("customer:read"));
    }

    private String credential(String systemId, List<String> scopes) {
        return world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "x", "scopes", scopes))).get("key").asString();
    }

    private JsonNode createEndpoint(Account account, String token, String url, List<String> types) {
        return world.body(world.call(HttpMethod.POST, account.host(), token, "/api/v1/webhook-endpoints",
                HttpWorld.Map2.of("url", url, "eventTypes", types, "description", "test")));
    }

    private JsonNode json(MvcResult result) {
        return world.body(result);
    }

    @Test
    void theSigningSecretIsShownExactlyOnceAndNeverRead() throws Exception {
        MvcResult created = world.call(HttpMethod.POST, acme.host(), manageKey, "/api/v1/webhook-endpoints",
                HttpWorld.Map2.of("url", "https://hooks.example.com/billing", "eventTypes", List.of("charge.succeeded")));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json(created);
        String secret = body.get("signingSecret").asString();
        String id = body.get("endpoint").get("id").asString();
        assertThat(secret).startsWith("whsec_");
        assertThat(body.get("endpoint").get("keyId").asString()).startsWith("whk_");

        String fetched = world.call(HttpMethod.GET, acme.host(), manageKey, "/api/v1/webhook-endpoints/" + id, null).getResponse().getContentAsString();
        String listed = world.call(HttpMethod.GET, acme.host(), manageKey, "/api/v1/webhook-endpoints", null).getResponse().getContentAsString();
        for (String text : List.of(fetched, listed)) {
            assertThat(text).doesNotContain(secret).doesNotContain("signingSecret").doesNotContain("ciphertext").doesNotContain("v1.k1.");
        }
        assertThat(json(world.call(HttpMethod.GET, acme.host(), manageKey, "/api/v1/webhook-endpoints/" + id, null)).get("keyId").asString())
                .isEqualTo(body.get("endpoint").get("keyId").asString());
    }

    @Test
    void rotatingIssuesANewSecretAndKeyId() {
        JsonNode created = createEndpoint(acme, manageKey, "https://hooks.example.com/a", List.of("*"));
        String id = created.get("endpoint").get("id").asString();
        JsonNode rotated = json(world.call(HttpMethod.POST, acme.host(), manageKey, "/api/v1/webhook-endpoints/" + id + "/rotate-secret", null));
        assertThat(rotated.get("signingSecret").asString()).isNotEqualTo(created.get("signingSecret").asString());
        assertThat(rotated.get("endpoint").get("keyId").asString()).isNotEqualTo(created.get("endpoint").get("keyId").asString());
        assertThat(rotated.get("endpoint").get("secretRotatedAt").isNull()).isFalse();
    }

    @Test
    void permissionsSeparateReadingFromManaging() {
        JsonNode created = createEndpoint(acme, manageKey, "https://hooks.example.com/b", List.of("*"));
        String id = created.get("endpoint").get("id").asString();

        assertThat(world.status(HttpMethod.GET, acme.host(), readKey, "/api/v1/webhook-endpoints", null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.GET, acme.host(), readKey, "/api/v1/webhook-deliveries", null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.POST, acme.host(), readKey, "/api/v1/webhook-endpoints",
                HttpWorld.Map2.of("url", "https://hooks.example.com/c", "eventTypes", List.of("*")))).isEqualTo(403);
        assertThat(world.status(HttpMethod.PATCH, acme.host(), readKey, "/api/v1/webhook-endpoints/" + id, HttpWorld.Map2.of("status", "DISABLED"))).isEqualTo(403);
        assertThat(world.status(HttpMethod.POST, acme.host(), readKey, "/api/v1/webhook-endpoints/" + id + "/rotate-secret", null)).isEqualTo(403);
        assertThat(world.status(HttpMethod.GET, acme.host(), noWebhookKey, "/api/v1/webhook-endpoints", null)).isEqualTo(403);
        assertThat(world.status(HttpMethod.GET, acme.host(), null, "/api/v1/webhook-endpoints", null)).isEqualTo(401);
        assertThat(world.status(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/webhook-endpoints", null)).as("a tenant administrator").isEqualTo(200);
    }

    @Test
    void invalidEndpointsAreRejected() {
        String host = acme.host();
        assertThat(world.status(HttpMethod.POST, host, manageKey, "/api/v1/webhook-endpoints",
                HttpWorld.Map2.of("url", "ftp://hooks.example.com/x", "eventTypes", List.of("*")))).isEqualTo(400);
        assertThat(world.status(HttpMethod.POST, host, manageKey, "/api/v1/webhook-endpoints",
                HttpWorld.Map2.of("url", "https://user:pw@hooks.example.com/x", "eventTypes", List.of("*")))).isEqualTo(400);
        assertThat(world.status(HttpMethod.POST, host, manageKey, "/api/v1/webhook-endpoints",
                HttpWorld.Map2.of("url", "https://hooks.example.com/x", "eventTypes", List.of("charge.succeded")))).as("typo").isEqualTo(400);
        assertThat(world.status(HttpMethod.POST, host, manageKey, "/api/v1/webhook-endpoints",
                HttpWorld.Map2.of("url", "https://hooks.example.com/x", "eventTypes", List.of()))).isEqualTo(400);
        assertThat(world.status(HttpMethod.POST, host, manageKey, "/api/v1/webhook-endpoints", HttpWorld.Map2.of("url", "https://hooks.example.com/x")))
                .as("no event types").isEqualTo(400);
    }

    @Test
    void anEndpointCanBeChangedAndPaused() {
        JsonNode created = createEndpoint(acme, manageKey, "https://hooks.example.com/d", List.of("charge.succeeded"));
        String id = created.get("endpoint").get("id").asString();
        JsonNode updated = json(world.call(HttpMethod.PATCH, acme.host(), manageKey, "/api/v1/webhook-endpoints/" + id,
                HttpWorld.Map2.of("url", "https://hooks.example.com/e", "eventTypes", List.of("charge.failed", "reservation.created"), "status", "DISABLED")));
        assertThat(updated.get("url").asString()).isEqualTo("https://hooks.example.com/e");
        assertThat(updated.get("status").asString()).isEqualTo("DISABLED");
        assertThat(updated.get("eventTypes")).hasSize(2);
        assertThat(world.status(HttpMethod.PATCH, acme.host(), manageKey, "/api/v1/webhook-endpoints/" + id, HttpWorld.Map2.of("status", "BROKEN"))).isEqualTo(400);
        assertThat(json(world.call(HttpMethod.PATCH, acme.host(), manageKey, "/api/v1/webhook-endpoints/" + id, HttpWorld.Map2.of("description", "only this")))
                .get("url").asString()).as("absent fields stay").isEqualTo("https://hooks.example.com/e");
    }

    @Test
    void anotherTenantCannotSeeOrTouchAnEndpoint() {
        String id = createEndpoint(acme, manageKey, "https://hooks.example.com/f", List.of("*")).get("endpoint").get("id").asString();
        assertThat(world.status(HttpMethod.GET, globex.host(), globex.adminToken(), "/api/v1/webhook-endpoints/" + id, null)).isEqualTo(404);
        assertThat(world.status(HttpMethod.PATCH, globex.host(), globex.adminToken(), "/api/v1/webhook-endpoints/" + id, HttpWorld.Map2.of("status", "DISABLED"))).isEqualTo(404);
        assertThat(world.status(HttpMethod.POST, globex.host(), globex.adminToken(), "/api/v1/webhook-endpoints/" + id + "/rotate-secret", null)).isEqualTo(404);
        assertThat(json(world.call(HttpMethod.GET, globex.host(), globex.adminToken(), "/api/v1/webhook-endpoints", null))).isEmpty();
    }

    @Test
    void aRealEventIsDeliveredAndItsHistoryAndResendAreVisibleOverHttp() throws Exception {
        TenantId tenant = TenantId.of(acme.tenantId());
        try (TestReceiver receiver = new TestReceiver()) {
            JsonNode endpoint = createEndpoint(acme, manageKey, receiver.url(), List.of("customer.created"));
            String endpointId = endpoint.get("endpoint").get("id").asString();

            MvcResult customer = world.call(HttpMethod.POST, acme.host(), manageKey, "/api/v1/customers", HttpWorld.Map2.of("displayName", "Hooked Co"));
            assertThat(customer.getResponse().getStatus()).as("create customer needs an Idempotency-Key").isEqualTo(400);
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/customers").header("Host", acme.host())
                    .header("Authorization", "Bearer " + manageKey).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"externalUserId\":\"u-" + UUID.randomUUID() + "\",\"displayName\":\"Hooked Co\"}");
            assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(201);

            scopes.runInTenant(tenant, relay::relayDue);
            scopes.runInTenant(tenant, deliver::runOnce);

            JsonNode list = json(world.call(HttpMethod.GET, acme.host(), readKey, "/api/v1/webhook-deliveries?status=SUCCEEDED&endpointId=" + endpointId, null));
            assertThat(list).hasSize(1);
            assertThat(list.get(0).get("eventType").asString()).isEqualTo("customer.created");
            String deliveryId = list.get(0).get("id").asString();

            JsonNode detail = json(world.call(HttpMethod.GET, acme.host(), readKey, "/api/v1/webhook-deliveries/" + deliveryId, null));
            assertThat(detail.get("attempts")).hasSize(1);
            assertThat(detail.get("attempts").get(0).get("statusCode").asInt()).isEqualTo(200);
            JsonNode delivered = world.body(world.call(HttpMethod.GET, acme.host(), readKey, "/api/v1/webhook-deliveries/" + deliveryId, null));
            String body = detail.get("body").asString();
            assertThat(delivered.get("body").asString()).isEqualTo(receiver.requests().get(0).body());
            JsonNode envelope = new tools.jackson.databind.json.JsonMapper().readTree(body);
            assertThat(envelope.get("type").asString()).isEqualTo("customer.created");
            assertThat(EventSchemas.validate("customer.created", envelope.get("data").toString())).isEmpty();

            assertThat(world.status(HttpMethod.POST, acme.host(), readKey, "/api/v1/webhook-deliveries/" + deliveryId + "/resend", null)).isEqualTo(403);
            MvcResult resent = world.call(HttpMethod.POST, acme.host(), manageKey, "/api/v1/webhook-deliveries/" + deliveryId + "/resend", null);
            assertThat(resent.getResponse().getStatus()).isEqualTo(202);
            assertThat(json(resent).get("resendOf").asString()).isEqualTo(deliveryId);
            scopes.runInTenant(tenant, deliver::runOnce);
            assertThat(receiver.requests()).hasSize(2);
            assertThat(receiver.requests().get(1).header("Billing-Event-Id")).isEqualTo(receiver.requests().get(0).header("Billing-Event-Id"));

            assertThat(world.status(HttpMethod.GET, acme.host(), readKey, "/api/v1/webhook-deliveries?status=NOPE", null)).isEqualTo(400);
            assertThat(world.status(HttpMethod.GET, acme.host(), readKey, "/api/v1/webhook-deliveries/" + UUID.randomUUID(), null)).isEqualTo(404);
            assertThat(world.status(HttpMethod.GET, globex.host(), globex.adminToken(), "/api/v1/webhook-deliveries/" + deliveryId, null)).isEqualTo(404);
        }
    }
}
