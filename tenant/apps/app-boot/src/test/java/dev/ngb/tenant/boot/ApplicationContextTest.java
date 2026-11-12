package dev.ngb.tenant.boot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.tenant.support.HttpIntegrationTest;
import org.junit.jupiter.api.Test;

/** The whole application context starts against a Flyway-migrated PostgreSQL. */
class ApplicationContextTest extends HttpIntegrationTest {

    @Test
    void everyBeanIsWired() {
        assertThat(mvc).isNotNull();
        assertThat(registerUser).isNotNull();
        assertThat(billing).isNotNull();
    }
}
