package dev.ngb.core_billing.support;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * A real Keycloak started from the repository's realm export, shared by every test in the JVM. Secrets
 * are random per run; nothing here is a real credential.
 */
public final class KeycloakFixture {

    public static final String REALM = "corebilling";
    public static final String ADMIN_CLIENT_ID = "billing-admin";
    public static final String TEST_CLIENT_ID = "billing-test";
    public static final String AUDIENCE = "core-billing";

    private static final String BOOTSTRAP_PASSWORD = UUID.randomUUID().toString();
    private static final String ADMIN_CLIENT_SECRET = UUID.randomUUID().toString();
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.8.2"))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "bootstrap-admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", BOOTSTRAP_PASSWORD)
            .withEnv("BILLING_ADMIN_CLIENT_SECRET", ADMIN_CLIENT_SECRET)
            .withCopyFileToContainer(MountableFile.forHostPath(realmExport()), "/opt/keycloak/data/import/realm-export.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM).forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

    private static boolean testClientReady;

    static {
        KEYCLOAK.start();
    }

    private KeycloakFixture() {}

    public static String baseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    public static String issuer() {
        return baseUrl() + "/realms/" + REALM;
    }

    public static String jwkSetUri() {
        return issuer() + "/protocol/openid-connect/certs";
    }

    public static String adminClientSecret() {
        return ADMIN_CLIENT_SECRET;
    }

    /** Sets a permanent password so the direct-grant login used by tests is allowed. */
    public static void setPermanentPassword(String subject, String password) {
        RestClient.create(baseUrl()).put()
                .uri("/admin/realms/{realm}/users/{id}/reset-password", REALM, subject)
                .headers(h -> h.setBearerAuth(bootstrapToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", password, "temporary", false))
                .retrieve()
                .toBodilessEntity();
    }

    /** Logs a user in with the test client and returns the access token. */
    public static String accessToken(String username, String password) {
        ensureTestClient();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", TEST_CLIENT_ID);
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        @SuppressWarnings("unchecked")
        Map<String, Object> token = RestClient.create(baseUrl()).post()
                .uri("/realms/{realm}/protocol/openid-connect/token", REALM)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return (String) token.get("access_token");
    }

    /** Administers the realm as the bootstrap admin, for assertions and cleanup. */
    public static RestClient adminApi() {
        return RestClient.builder()
                .baseUrl(baseUrl() + "/admin/realms/" + REALM)
                .defaultHeaders(h -> h.setBearerAuth(bootstrapToken()))
                .build();
    }

    private static synchronized void ensureTestClient() {
        if (testClientReady) {
            return;
        }
        RestClient admin = adminApi();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> web = admin.get().uri("/clients?clientId=billing-web").retrieve().body(List.class);
        // Reuse the real client's mappers so the test token carries exactly the claims production tokens do.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappers = ((List<Map<String, Object>>) web.getFirst().get("protocolMappers")).stream()
                .map(mapper -> {
                    Map<String, Object> copy = new java.util.LinkedHashMap<>(mapper);
                    copy.remove("id");
                    return copy;
                })
                .toList();
        Map<String, Object> client = new java.util.LinkedHashMap<>();
        client.put("clientId", TEST_CLIENT_ID);
        client.put("publicClient", true);
        client.put("directAccessGrantsEnabled", true);
        client.put("standardFlowEnabled", false);
        client.put("protocolMappers", mappers);
        admin.post().uri("/clients").contentType(MediaType.APPLICATION_JSON).body(client).retrieve().toBodilessEntity();
        testClientReady = true;
    }

    private static String bootstrapToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", "bootstrap-admin");
        form.add("password", BOOTSTRAP_PASSWORD);
        @SuppressWarnings("unchecked")
        Map<String, Object> token = RestClient.create(baseUrl()).post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return (String) token.get("access_token");
    }

    private static Path realmExport() {
        return RepositoryPaths.repositoryRoot().resolve("ops/keycloak/realm-export.json");
    }
}
