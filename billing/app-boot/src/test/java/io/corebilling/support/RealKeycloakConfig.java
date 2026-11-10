package io.corebilling.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Points token validation and identity administration at the shared real Keycloak. */
@TestConfiguration(proxyBeanMethods = false)
public class RealKeycloakConfig {

    @Bean
    DynamicPropertyRegistrar keycloakProperties() {
        return registry -> {
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakFixture::issuer);
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", KeycloakFixture::jwkSetUri);
            registry.add("billing.keycloak.base-url", KeycloakFixture::baseUrl);
            registry.add("billing.keycloak.admin-client-secret", KeycloakFixture::adminClientSecret);
        };
    }
}
