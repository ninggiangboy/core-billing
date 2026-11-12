package dev.ngb.sampletenant.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.sampletenant.billing.api.client.BillingPricing;
import dev.ngb.sampletenant.billing.api.client.BillingSubscriptions;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.customer.domain.customer.Customer;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.user.command.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.resource.api.execution.ExecutionQueue.Claimed;
import dev.ngb.sampletenant.resource.application.order.command.CreateOrderUseCase;
import dev.ngb.sampletenant.resource.application.order.port.OrderQueueRepository;
import dev.ngb.sampletenant.resource.application.order.port.OrderRepository;
import dev.ngb.sampletenant.resource.application.order.query.OrderReadRepository;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

class CreateOrderUseCaseIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    CatalogLookup catalog;

    @Autowired
    CustomerDirectory customerDirectory;

    @Autowired
    @Qualifier("billingPricing")
    BillingPricing pricing;

    @Autowired
    @Qualifier("billingSubscriptions")
    BillingSubscriptions subscriptions;

    @Autowired
    @Qualifier("billingWallets")
    BillingWallets wallets;

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderReadRepository read;

    @Autowired
    UnitOfWork unitOfWork;

    @Autowired
    CustomerRepository customers;

    @Autowired
    CatalogPlanRepository plans;

    @Autowired
    Clock clock;

    @Autowired
    IdGenerator ids;

    @Test
    void aFailingEnqueueLeavesNoOrderBehind() {
        String email = "atomic-" + UUID.randomUUID() + "@example.com";
        User user = registerUser.execute(
                new RegisterUserUseCase.Command(email, "Ada", HttpWorld.PASSWORD, Role.VIEWER));
        Customer customer = customers.findByUserId(user.id()).orElseThrow();
        CatalogPlan plan = new CatalogPlan(
                UUID.randomUUID(), "atomic-" + UUID.randomUUID(), "Compute",
                ResourceType.COMPUTE, "description", "bplan_1", true, NOW);
        plans.save(plan);

        OrderQueueRepository failing = new OrderQueueRepository() {
            @Override
            public void enqueue(UUID orderId, Instant enqueuedAt) {
                throw new IllegalStateException("queue is down");
            }

            @Override
            public List<Claimed> claim(int batchSize) {
                return List.of();
            }

            @Override
            public void finish(UUID orderId) {}

            @Override
            public void requeue(UUID orderId) {}
        };
        CreateOrderUseCase create = new CreateOrderUseCase(
                catalog, customerDirectory, pricing, subscriptions, wallets, orders, failing, unitOfWork,
                clock, ids, Duration.ofMinutes(15));

        assertThatThrownBy(() -> create.execute(new CreateOrderUseCase.Command(user.id(), plan.id(), "120", null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(read.forCustomer(customer.id())).isEmpty();
    }
}
