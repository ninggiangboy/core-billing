package dev.ngb.core_billing.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.jobs.MoneyJobs;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The heavy verify run is a singleton and records one run per tenant per day. */
@SpringBootTest(classes = Application.class)
class SingletonJobIntegrationTest {

    private static final String VERIFY = "verify-ledger";
    private static final String BOOTSTRAP = "platform-bootstrap";
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final JdbcTemplate OWNER = new JdbcTemplate(new DriverManagerDataSource(
            PostgresContainerConfig.postgres().getJdbcUrl(),
            PostgresContainerConfig.postgres().getUsername(),
            PostgresContainerConfig.postgres().getPassword()));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgreSQLContainer postgres = PostgresContainerConfig.postgres();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> PostgresContainerConfig.APP_ROLE);
        registry.add("spring.datasource.password", () -> PostgresContainerConfig.APP_PASSWORD);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.appDbPassword", () -> PostgresContainerConfig.APP_PASSWORD);
        registry.add("billing.jobs.enabled", () -> "true");
        registry.add("billing.secrets.master-key", () -> PostgresContainerConfig.TEST_MASTER_KEY);
    }

    @Autowired MoneyJobs moneyJobs;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void anActiveTenantExists() {
        OWNER.update(
                "insert into tenants (id, code, name, status, created_at) values (?, 'verify-tenant', 'Verify', 'ACTIVE', now()) on conflict (id) do nothing",
                TENANT);
    }

    @Test
    void aSecondVerificationTheSameDayDoesNoWork() {
        String runKey = LocalDate.now(ZoneOffset.UTC).toString();

        moneyJobs.verifyLedgers();
        Integer first = runs(runKey);
        assertThat(first).as("every active tenant gets one run record").isPositive();

        moneyJobs.verifyLedgers();
        assertThat(runs(runKey)).as("the second run claims nothing").isEqualTo(first);
        assertThat(lease(VERIFY)).as("the lease is released after the round").isZero();
    }

    @Test
    void thePlatformBootstrapRunsOnceAndReleasesItsLease() {
        assertThat(lease(BOOTSTRAP)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from permissions", Integer.class)).isPositive();
    }

    private Integer runs(String runKey) {
        return jdbc.queryForObject(
                "select count(*) from job_runs where job = ? and run_key = ?", Integer.class, VERIFY, runKey);
    }

    private Integer lease(String name) {
        return jdbc.queryForObject("select count(*) from job_leases where name = ?", Integer.class, name);
    }
}
