package io.corebilling.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container shared by every integration test in the JVM; the image matches
 * docker-compose.yml. Flyway connects as the container's superuser (the table owner) and the
 * application connects as the unprivileged corebilling_app role, exactly as in production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    public static final String IMAGE = "postgres:18";
    public static final String APP_ROLE = "corebilling_app";
    public static final String APP_PASSWORD = "app-role-test-secret";
    /** 32 bytes, base64: a fixed key for tests only. */
    public static final String TEST_MASTER_KEY = "dGVzdC1tYXN0ZXIta2V5LTMyLWJ5dGVzLWxvbmctISE=";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE);

    static {
        POSTGRES.start();
    }

    @Bean
    DynamicPropertyRegistrar databaseProperties() {
        return registry -> {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", () -> APP_ROLE);
            registry.add("spring.datasource.password", () -> APP_PASSWORD);
            registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
            registry.add("spring.flyway.user", POSTGRES::getUsername);
            registry.add("spring.flyway.password", POSTGRES::getPassword);
            registry.add("spring.flyway.placeholders.appDbPassword", () -> APP_PASSWORD);
            // Tests drive the background jobs directly and deterministically.
            registry.add("billing.jobs.enabled", () -> "false");
            registry.add("billing.secrets.master-key", () -> TEST_MASTER_KEY);
            // Delivery tests use a receiver on loopback over http.
            registry.add("billing.webhooks.allow-insecure-targets", () -> "true");
            // A small budget keeps the retry-to-dead-letter tests short.
            registry.add("billing.webhooks.max-attempts", () -> "3");
            registry.add("billing.outbox.max-attempts", () -> "3");
        };
    }

    @Bean
    OwnerDatabase ownerDatabase() {
        return new OwnerDatabase(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
