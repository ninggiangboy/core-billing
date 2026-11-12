package dev.ngb.sampletenant.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container shared by every integration test in the JVM; the image matches
 * docker-compose.yml. The sample schema has no row-level security and no separate application role,
 * so Flyway and the application both connect as the container's superuser.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    public static final String IMAGE = "postgres:18";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE);

    static {
        POSTGRES.start();
    }

    /** Shared with tests that build their own property source instead of importing this configuration. */
    public static PostgreSQLContainer postgres() {
        return POSTGRES;
    }

    @Bean
    DynamicPropertyRegistrar databaseProperties() {
        return registry -> {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
            registry.add("spring.flyway.user", POSTGRES::getUsername);
            registry.add("spring.flyway.password", POSTGRES::getPassword);
            // Many cached Spring contexts share one container; small idle pools keep them under its connection limit.
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
            registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
            // Tests drive the execution worker directly and deterministically.
            registry.add("sampletenant.execution.enabled", () -> "false");
        };
    }
}
