package dev.ngb.core_billing.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Builds real tenants and users (in Keycloak and Billing) and calls the API over MockMvc with real tokens. */
public final class HttpWorld {

    public static final String PASSWORD = "Perm-pass-1";

    public record Account(String tenantId, String code, String host, String adminEmail, String adminToken) {}

    private final MockMvc mvc;
    private final BootstrapPlatformAdminUseCase bootstrap;
    private final ScopeTestSupport scopes;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseDomain;
    private String platformToken;

    public HttpWorld(MockMvc mvc, BootstrapPlatformAdminUseCase bootstrap, ScopeTestSupport scopes, String baseDomain) {
        this.mvc = mvc;
        this.bootstrap = bootstrap;
        this.scopes = scopes;
        this.baseDomain = baseDomain;
    }

    public String platformHost() {
        return "platform." + baseDomain;
    }

    public synchronized String platformAdminToken() {
        if (platformToken == null) {
            String email = "root-" + UUID.randomUUID() + "@example.com";
            scopes.runOnPlatform(() -> bootstrap.execute(email, "Root", "Temp-pass-1"));
            String subject = lookupSubject("platform:" + email);
            KeycloakFixture.setPermanentPassword(subject, PASSWORD);
            platformToken = KeycloakFixture.accessToken("platform:" + email, PASSWORD);
        }
        return platformToken;
    }

    /** Provisions a tenant through the real endpoint, as the platform administrator, with a first admin. */
    public Account newTenant() {
        String code = "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        JsonNode created = body(call(HttpMethod.POST, platformHost(), platformAdminToken(), "/api/v1/tenants", Map2.of(
                "code", code, "name", "Tenant " + code, "adminEmail", email, "adminDisplayName", "Admin", "adminTemporaryPassword", "Temp-pass-1")));
        String subject = lookupSubject(code + ":" + email);
        KeycloakFixture.setPermanentPassword(subject, PASSWORD);
        return new Account(created.get("id").asString(), code, code + "." + baseDomain, email,
                KeycloakFixture.accessToken(code + ":" + email, PASSWORD));
    }

    /** Creates a user in the tenant through the real endpoint with the given roles and returns a token for it. */
    public String newUserToken(Account account, Set<String> roleIds) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        body(call(HttpMethod.POST, account.host(), account.adminToken(), "/api/v1/users", Map2.of(
                "email", email, "displayName", "User", "temporaryPassword", "Temp-pass-1", "roleIds", roleIds)));
        KeycloakFixture.setPermanentPassword(lookupSubject(account.code() + ":" + email), PASSWORD);
        return KeycloakFixture.accessToken(account.code() + ":" + email, PASSWORD);
    }

    public String createRole(Account account, String name, Set<String> permissions) {
        return body(call(HttpMethod.POST, account.host(), account.adminToken(), "/api/v1/roles", Map2.of("name", name, "permissions", permissions)))
                .get("id").asString();
    }

    public MvcResult call(HttpMethod method, String host, String token, String path, Object payload) {
        try {
            MockHttpServletRequestBuilder builder = request(method, path).header("Host", host);
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if (payload != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
            }
            return mvc.perform(builder).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public int status(HttpMethod method, String host, String token, String path, Object payload) {
        return call(method, host, token, path, payload).getResponse().getStatus();
    }

    public JsonNode body(MvcResult result) {
        try {
            int status = result.getResponse().getStatus();
            if (status >= 300) {
                throw new IllegalStateException("unexpected status " + status + ": " + result.getResponse().getContentAsString());
            }
            return json.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String lookupSubject(String username) {
        JsonNode users = JsonMapper.builder().build().readTree(
                KeycloakFixture.adminApi().get().uri("/users?username={u}&exact=true", username).retrieve().body(String.class));
        return users.get(0).get("id").asString();
    }

    /** Tiny map builder that accepts mixed value types without unchecked generics. */
    public static final class Map2 {
        private Map2() {}

        public static java.util.Map<String, Object> of(Object... keyValues) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < keyValues.length; i += 2) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
            return map;
        }
    }
}
