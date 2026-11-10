package io.corebilling.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One PostgreSQL container shared by every integration test in the JVM; matches docker-compose.yml. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    public static final String IMAGE = "postgres:18";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
