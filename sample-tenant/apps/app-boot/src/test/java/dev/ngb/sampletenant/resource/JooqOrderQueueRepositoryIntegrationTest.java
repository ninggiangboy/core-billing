package dev.ngb.sampletenant.resource;

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
import dev.ngb.sampletenant.resource.api.execution.ExecutionQueue.Claimed;
import dev.ngb.sampletenant.resource.application.order.port.OrderQueueRepository;
import dev.ngb.sampletenant.resource.application.order.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.order.Order;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqOrderQueueRepositoryIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    OrderQueueRepository queue;

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

    private UUID order() {
        String email = "queue-" + UUID.randomUUID() + "@example.com";
        User user = registerUser.execute(
                new RegisterUserUseCase.Command(email, "Ada", HttpWorld.PASSWORD, Role.VIEWER));
        Customer customer = customers.findByUserId(user.id()).orElseThrow();
        CatalogPlan plan = new CatalogPlan(
                UUID.randomUUID(), "queue-" + UUID.randomUUID(), "Compute",
                ResourceType.COMPUTE, "description", "bplan_1", true, NOW);
        plans.save(plan);
        Order order = Order.queued(
                ids, customer.id(), customer.billingCustomerId(), "wal_1", plan.id(), "bplan_1",
                "TIME_BASED", "compute.vcpu_minute", "120", "vCPU-minute", null, 1000, "USD", null, null, NOW);
        orders.save(order);
        return order.id();
    }

    @Test
    void claimsFinishesAndRequeues() {
        UUID orderId = order();
        queue.enqueue(orderId, NOW);

        List<Claimed> first = queue.claim(10);
        assertThat(first).containsExactly(new Claimed(orderId, 1));
        assertThat(queue.claim(10)).isEmpty();

        queue.finish(orderId);
        assertThat(queue.claim(10)).isEmpty();

        queue.requeue(orderId);
        assertThat(queue.claim(10)).containsExactly(new Claimed(orderId, 2));
    }

    @Test
    void twoClaimersTakeDisjointBatches() throws Exception {
        UUID first = order();
        UUID second = order();
        queue.enqueue(first, NOW);
        queue.enqueue(second, NOW);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<Claimed>> a = pool.submit(() -> queue.claim(1));
            Future<List<Claimed>> b = pool.submit(() -> queue.claim(1));
            List<Claimed> batchA = a.get();
            List<Claimed> batchB = b.get();

            assertThat(batchA).hasSize(1);
            assertThat(batchB).hasSize(1);
            assertThat(batchA.get(0).orderId()).isNotEqualTo(batchB.get(0).orderId());
            assertThat(batchA.get(0).attempts()).isEqualTo(1);
            assertThat(batchB.get(0).attempts()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
