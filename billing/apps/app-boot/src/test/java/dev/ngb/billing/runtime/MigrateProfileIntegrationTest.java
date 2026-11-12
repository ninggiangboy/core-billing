package dev.ngb.billing.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.billing.Application;
import dev.ngb.billing.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Migrate is a non-web Flyway run as the owner; nothing else starts. */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("migrate")
class MigrateProfileIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgreSQLContainer postgres = PostgresContainerConfig.postgres();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> PostgresContainerConfig.APP_ROLE);
        registry.add("spring.datasource.password", () -> PostgresContainerConfig.APP_PASSWORD);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.appDbPassword", () -> PostgresContainerConfig.APP_PASSWORD);
        registry.add("billing.secrets.master-key", () -> PostgresContainerConfig.TEST_MASTER_KEY);
        registry.add("billing.migrate.exit", () -> "false");
    }

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theMigrateStepRunsFlywayAsTheOwner() {
        assertThat(jdbc.queryForObject("select to_regclass('public.job_leases')::text", String.class))
                .isEqualTo("job_leases");
    }

    @Test
    void theMigrateStepStartsNoWebServer() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
    }
}
