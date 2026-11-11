package dev.ngb.core_billing.identity;

import dev.ngb.core_billing.platform.api.error.ConflictException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.platform.adapter.keycloak.KeycloakIdentityProvider;
import dev.ngb.core_billing.platform.application.auth.port.IdentityProviderPort.NewIdentity;
import dev.ngb.core_billing.support.KeycloakFixture;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class KeycloakIdentityProviderIntegrationTest {

    private final KeycloakIdentityProvider provider = new KeycloakIdentityProvider(
            RestClient.builder(), KeycloakFixture.baseUrl(), KeycloakFixture.REALM,
            KeycloakFixture.ADMIN_CLIENT_ID, KeycloakFixture.adminClientSecret());

    private static Map<String, Object> claims(String jwt) {
        String payload = jwt.split("\\.")[1];
        return JsonMapper.builder().build().readValue(Base64.getUrlDecoder().decode(payload), new TypeReference<Map<String, Object>>() {});
    }

    @Test
    void createsATenantScopedIdentityWhoseTokenCarriesTheTenantAndAudience() {
        String email = "alice-" + UUID.randomUUID() + "@example.com";
        String subject = provider.createIdentity(new NewIdentity("acme:" + email, email, "Alice", "acme", "Temp-pass-1"));
        KeycloakFixture.setPermanentPassword(subject, "Perm-pass-1");

        Map<String, Object> claims = claims(KeycloakFixture.accessToken("acme:" + email, "Perm-pass-1"));

        assertThat(claims.get("sub")).isEqualTo(subject);
        assertThat(claims.get("tenant_code")).isEqualTo("acme");
        assertThat(claims.get("iss")).isEqualTo(KeycloakFixture.issuer());
        assertThat(String.valueOf(claims.get("aud"))).contains(KeycloakFixture.AUDIENCE);
    }

    @Test
    void theSameEmailInTwoTenantsIsTwoIndependentIdentities() {
        String email = "bob-" + UUID.randomUUID() + "@example.com";
        String inAcme = provider.createIdentity(new NewIdentity("acme:" + email, email, "Bob", "acme", null));
        String inGlobex = provider.createIdentity(new NewIdentity("globex:" + email, email, "Bob", "globex", null));
        KeycloakFixture.setPermanentPassword(inAcme, "Perm-pass-1");
        KeycloakFixture.setPermanentPassword(inGlobex, "Perm-pass-2");

        assertThat(inAcme).isNotEqualTo(inGlobex);
        assertThat(claims(KeycloakFixture.accessToken("acme:" + email, "Perm-pass-1")).get("tenant_code")).isEqualTo("acme");
        assertThat(claims(KeycloakFixture.accessToken("globex:" + email, "Perm-pass-2")).get("tenant_code")).isEqualTo("globex");
    }

    @Test
    void aDuplicateUsernameIsAConflict() {
        String email = "carol-" + UUID.randomUUID() + "@example.com";
        provider.createIdentity(new NewIdentity("acme:" + email, email, "Carol", "acme", null));
        assertThatThrownBy(() -> provider.createIdentity(new NewIdentity("acme:" + email, email, "Carol", "acme", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aDeletedIdentityCanNoLongerLogIn() {
        String email = "dave-" + UUID.randomUUID() + "@example.com";
        String subject = provider.createIdentity(new NewIdentity("acme:" + email, email, "Dave", "acme", null));
        KeycloakFixture.setPermanentPassword(subject, "Perm-pass-1");
        assertThat(KeycloakFixture.accessToken("acme:" + email, "Perm-pass-1")).isNotBlank();

        provider.deleteIdentity(subject);

        assertThatThrownBy(() -> KeycloakFixture.accessToken("acme:" + email, "Perm-pass-1"))
                .isInstanceOf(RestClientResponseException.class);
        provider.deleteIdentity(subject); // deleting twice is harmless
    }
}
