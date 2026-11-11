package dev.ngb.core_billing.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.config.SchedulingConfiguration;
import dev.ngb.core_billing.boot.jobs.InvoicingJobs;
import dev.ngb.core_billing.boot.jobs.MoneyJobs;
import dev.ngb.core_billing.boot.jobs.OutboxJobs;
import dev.ngb.core_billing.boot.jobs.PaymentJobs;
import dev.ngb.core_billing.boot.jobs.TenantSweep;
import dev.ngb.core_billing.boot.jobs.WebhookJobs;
import dev.ngb.core_billing.customer.adapter.customer.web.CustomerController;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The api edge serves HTTP and creates no job bean, even when the job switch is on. */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("api")
class ApiProfileIntegrationTest {

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

    @Autowired ApplicationContext context;

    @Test
    void theApiProfileCreatesNoJobBean() {
        for (Class<?> job : new Class<?>[] {
                SchedulingConfiguration.class, OutboxJobs.class, MoneyJobs.class, PaymentJobs.class,
                InvoicingJobs.class, WebhookJobs.class, TenantSweep.class}) {
            assertThat(context.getBeanNamesForType(job)).as(job.getSimpleName()).isEmpty();
        }
    }

    @Test
    void theApiProfileKeepsTheBusinessEndpoints() {
        assertThat(context.getBeanNamesForType(CustomerController.class)).isNotEmpty();
    }
}
