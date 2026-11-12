package dev.ngb.billing.platform.adapter.auth.keycloak;

import dev.ngb.billing.platform.api.error.ConflictException;
import dev.ngb.billing.platform.application.auth.port.IdentityProviderPort;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Creates and deletes identities through the Keycloak Admin REST API with a service-account client. It
 * never reads or stores roles or permissions; those stay in Billing.
 */
public class KeycloakIdentityProvider implements IdentityProviderPort {

    private static final Duration TOKEN_SAFETY_MARGIN = Duration.ofSeconds(30);

    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    public KeycloakIdentityProvider(RestClient.Builder builder, String baseUrl, String realm, String clientId, String clientSecret) {
        this.http = builder.baseUrl(baseUrl).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public String createIdentity(NewIdentity identity) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("username", identity.username());
        body.put("email", identity.email());
        body.put("firstName", identity.displayName());
        body.put("enabled", true);
        body.put("emailVerified", true);
        body.put("attributes", Map.of("tenant_code", List.of(identity.tenantCode())));
        if (identity.temporaryPassword() != null) {
            body.put("credentials", List.of(Map.of("type", "password", "value", identity.temporaryPassword(), "temporary", true)));
        }
        try {
            URI location = http.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .headers(h -> h.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
            if (location == null) {
                throw new IllegalStateException("identity provider did not return the new identity");
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(409))) {
                throw new ConflictException("an identity with this username already exists");
            }
            throw new IllegalStateException("identity provider rejected the request (HTTP " + e.getStatusCode().value() + ")");
        }
    }

    @Override
    public void deleteIdentity(String subject) {
        try {
            http.delete()
                    .uri("/admin/realms/{realm}/users/{id}", realm, subject)
                    .headers(h -> h.setBearerAuth(adminToken()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                throw new IllegalStateException("identity provider could not delete the identity (HTTP " + e.getStatusCode().value() + ")");
            }
        }
    }

    private synchronized String adminToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedUntil)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> token = http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (token == null || !(token.get("access_token") instanceof String accessToken)) {
                throw new IllegalStateException("identity provider returned no admin token");
            }
            long expiresIn = token.get("expires_in") instanceof Number n ? n.longValue() : 60;
            cachedToken = accessToken;
            cachedUntil = Instant.now().plusSeconds(expiresIn).minus(TOKEN_SAFETY_MARGIN);
            return accessToken;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("identity provider refused the admin client (HTTP " + e.getStatusCode().value() + ")");
        }
    }
}
