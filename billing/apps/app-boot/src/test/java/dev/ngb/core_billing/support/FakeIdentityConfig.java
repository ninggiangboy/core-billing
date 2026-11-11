package dev.ngb.core_billing.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces Keycloak with a recorder for tests that only care about what Billing asks of it. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeIdentityConfig {

    @Bean
    @Primary
    RecordingIdentityProvider recordingIdentityProvider() {
        return new RecordingIdentityProvider();
    }
}
