package dev.ngb.billing.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import dev.ngb.billing.Application;
import dev.ngb.billing.boot.jobs.MoneyJobs;
import dev.ngb.billing.money.application.ledger.command.VerifyLedgerUseCase;
import dev.ngb.billing.platform.api.tenant.TenantContext;
import dev.ngb.billing.support.PostgresContainerConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The heavy verify run is a singleton and records one run per tenant per day. */
@SpringBootTest(classes = Application.class)
class SingletonJobIntegrationTest {

    private static final String VERIFY = "verify-ledger";
    private static final String BOOTSTRAP = "platform-bootstrap";
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-7222-8222-222222222222");
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
        // Tests call the job directly; the scheduler must not race them.
        registry.add("billing.jobs.verify-ledger-initial-delay", () -> "PT1H");
        registry.add("billing.jobs.verify-ledger-not-before", () -> "00:00");
        registry.add("billing.secrets.master-key", () -> PostgresContainerConfig.TEST_MASTER_KEY);
    }

    @Autowired MoneyJobs moneyJobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantContext tenantContext;
    @MockitoSpyBean VerifyLedgerUseCase verifyLedger;

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
    void aTenantWhoseVerificationFailsIsTriedAgainOnALaterTick() {
        OWNER.update(
                "insert into tenants (id, code, name, status, created_at) values (?, 'verify-tenant-2', 'Verify 2', 'ACTIVE', now()) on conflict (id) do nothing",
                OTHER_TENANT);
        String runKey = LocalDate.now(ZoneOffset.UTC).toString();
        // Start from an unclaimed day so the test does not depend on which test ran first.
        OWNER.update("delete from job_runs where job = ? and run_key = ?", VERIFY, runKey);
        AtomicBoolean failing = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failing.get() && TENANT.equals(tenantContext.current().dataTenantId().value())) {
                throw new IllegalStateException("the ledger read failed");
            }
            return invocation.callRealMethod();
        }).when(verifyLedger).execute();

        moneyJobs.verifyLedgers();

        assertThat(claim(runKey, TENANT)).as("a failed verification gives its claim back").isZero();
        assertThat(claim(runKey, OTHER_TENANT)).as("the round still covers the other tenants").isEqualTo(1);

        failing.set(false);
        moneyJobs.verifyLedgers();

        assertThat(claim(runKey, TENANT)).as("the next tick tries the failed tenant again").isEqualTo(1);
    }

    @Test
    void anUnfinishedClaimLeftByADeadWorkerIsTakenOver() {
        String runKey = LocalDate.now(ZoneOffset.UTC).toString();
        OWNER.update("delete from job_runs where job = ? and run_key = ?", VERIFY, runKey);
        OWNER.update(
                "insert into job_runs (job, run_key, tenant_id, started_at) values (?, ?, ?, now() - interval '31 minutes')",
                VERIFY, runKey, TENANT);

        moneyJobs.verifyLedgers();

        assertThat(finishedAt(runKey, TENANT)).as("the tenant is verified despite the abandoned claim").isNotNull();
    }

    @Test
    void aFinishedClaimIsNotTakenOverEvenAfterTheTtl() {
        String runKey = LocalDate.now(ZoneOffset.UTC).toString();
        OWNER.update("delete from job_runs where job = ? and run_key = ?", VERIFY, runKey);
        OWNER.update(
                "insert into job_runs (job, run_key, tenant_id, started_at, finished_at)"
                        + " values (?, ?, ?, now() - interval '31 minutes', now() - interval '30 minutes')",
                VERIFY, runKey, TENANT);
        Instant started = startedAt(runKey, TENANT);

        moneyJobs.verifyLedgers();

        assertThat(startedAt(runKey, TENANT)).as("a finished run is not repeated").isEqualTo(started);
        assertThat(finishedAt(runKey, TENANT)).isNotNull();
    }

    @Test
    void thePlatformBootstrapRunsOnceAndReleasesItsLease() {
        assertThat(lease(BOOTSTRAP)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from permissions", Integer.class)).isPositive();
    }

    private Integer claim(String runKey, UUID tenant) {
        return jdbc.queryForObject(
                "select count(*) from job_runs where job = ? and run_key = ? and tenant_id = ?", Integer.class, VERIFY, runKey, tenant);
    }

    private Integer runs(String runKey) {
        return jdbc.queryForObject(
                "select count(*) from job_runs where job = ? and run_key = ?", Integer.class, VERIFY, runKey);
    }

    private Instant startedAt(String runKey, UUID tenant) {
        return jdbc.queryForObject(
                "select started_at from job_runs where job = ? and run_key = ? and tenant_id = ?", Instant.class, VERIFY, runKey, tenant);
    }

    private Instant finishedAt(String runKey, UUID tenant) {
        return jdbc.queryForObject(
                "select finished_at from job_runs where job = ? and run_key = ? and tenant_id = ?", Instant.class, VERIFY, runKey, tenant);
    }

    private Integer lease(String name) {
        return jdbc.queryForObject("select count(*) from job_leases where name = ?", Integer.class, name);
    }
}
