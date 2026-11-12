package dev.ngb.sampletenant.usage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.customer.domain.customer.Customer;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.user.command.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.resource.application.order.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.order.Order;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import dev.ngb.sampletenant.usage.application.usage.port.UsageRepository;
import dev.ngb.sampletenant.usage.application.usage.query.UsageReadRepository;
import dev.ngb.sampletenant.usage.domain.usage.UsageRecord;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqUsageRepositoryIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    UsageRepository usage;

    @Autowired
    UsageReadRepository read;

    @Autowired
    OrderRepository orders;

    @Autowired
    CustomerRepository customers;

    @Autowired
    CatalogPlanRepository plans;

    @Autowired
    Clock clock;

    @Autowired
    IdGenerator ids;

    private UUID existingOrder() {
        String email = "usage-" + UUID.randomUUID() + "@example.com";
        User user = registerUser.execute(
                new RegisterUserUseCase.Command(email, "Ada", HttpWorld.PASSWORD, Role.VIEWER));
        Customer customer = customers.findByUserId(user.id()).orElseThrow();
        CatalogPlan plan = new CatalogPlan(
                UUID.randomUUID(), "usage-" + UUID.randomUUID(), "Compute",
                ResourceType.COMPUTE, "description", "bplan_1", true, NOW);
        plans.save(plan);
        Order order = Order.queued(
                ids, customer.id(), customer.billingCustomerId(), "wal_test", plan.id(), "bplan_1",
                "TIME_BASED", "compute.vcpu_minute", "120", "vCPU-minute", null, 1000, "USD", null, null, NOW);
        orders.save(order);
        return order.id();
    }

    @Test
    void roundTripsARecord() {
        UUID orderId = existingOrder();
        String customerId = customerId();
        String key = key();
        UsageRecord record = UsageRecord.recorded(
                ids, orderId, customerId, "compute.vcpu_minute", "120", "vCPU-minute", "use_1", key, NOW, NOW);

        usage.save(record);

        UsageRecord loaded = usage.findByOrderIdAndMetric(orderId, "compute.vcpu_minute").orElseThrow();
        assertThat(loaded.id()).isEqualTo(record.id());
        assertThat(loaded.billingCustomerId()).isEqualTo(customerId);
        assertThat(loaded.billingEventId()).isEqualTo("use_1");
        assertThat(loaded.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void aSecondInsertForTheSameOrderAndMetricIsIgnored() {
        UUID orderId = existingOrder();
        String customerId = customerId();
        usage.save(UsageRecord.recorded(
                ids, orderId, customerId, "compute.vcpu_minute", "120", "vCPU-minute", "use_1", key(), NOW, NOW));
        usage.save(UsageRecord.recorded(
                ids, orderId, customerId, "compute.vcpu_minute", "999", "vCPU-minute", "use_2", key(), NOW, NOW));

        assertThat(usage.findByOrderIdAndMetric(orderId, "compute.vcpu_minute").orElseThrow().billingEventId())
                .isEqualTo("use_1");
        assertThat(read.forBillingCustomer(customerId)).hasSize(1);
    }

    @Test
    void theReadSideSelectsIntoViews() {
        UUID orderId = existingOrder();
        String customerId = customerId();
        usage.save(UsageRecord.recorded(
                ids, orderId, customerId, "compute.vcpu_minute", "120", "vCPU-minute", "use_1", key(), NOW, NOW));

        var views = read.forBillingCustomer(customerId);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).orderId()).isEqualTo(orderId);
        assertThat(views.get(0).metric()).isEqualTo("compute.vcpu_minute");
        assertThat(views.get(0).billingEventId()).isEqualTo("use_1");
        assertThat(views.get(0).recordedAt()).isEqualTo(NOW);
    }

    private static String customerId() {
        return "cus_" + UUID.randomUUID();
    }

    private static String key() {
        return "usage:" + UUID.randomUUID();
    }
}
