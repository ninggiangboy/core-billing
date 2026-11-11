package dev.ngb.core_billing.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.jobs.InvoicingJobs;
import dev.ngb.core_billing.boot.jobs.MoneyJobs;
import dev.ngb.core_billing.boot.jobs.OutboxJobs;
import dev.ngb.core_billing.boot.jobs.PaymentJobs;
import dev.ngb.core_billing.boot.jobs.WebhookJobs;
import dev.ngb.core_billing.customer.adapter.customer.web.CustomerController;
import dev.ngb.core_billing.money.adapter.wallet.web.WalletController;
import dev.ngb.core_billing.payment.adapter.stripe.web.StripeWebhookController;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The worker owns every job and serves no business endpoint, but still answers its probes. */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("worker")
class WorkerProfileIntegrationTest {

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
    @Autowired MockMvc mvc;

    @Test
    void theWorkerProfileHasEveryJob() {
        for (Class<?> job : new Class<?>[] {OutboxJobs.class, MoneyJobs.class, PaymentJobs.class, InvoicingJobs.class, WebhookJobs.class}) {
            assertThat(context.getBeanNamesForType(job)).as(job.getSimpleName()).isNotEmpty();
        }
    }

    @Test
    void theWorkerProfileHasNoBusinessEndpoint() {
        for (Class<?> controller : new Class<?>[] {CustomerController.class, WalletController.class, StripeWebhookController.class}) {
            assertThat(context.getBeanNamesForType(controller)).as(controller.getSimpleName()).isEmpty();
        }
    }

    @Test
    void theWorkerStillAnswersItsProbes() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
