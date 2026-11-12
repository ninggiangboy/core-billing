package dev.ngb.sampletenant.resource;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.ngb.sampletenant.resource.application.order.query.OrderReadRepository;
import dev.ngb.sampletenant.resource.domain.order.Order;
import dev.ngb.sampletenant.resource.domain.order.OrderStatus;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqOrderRepositoryIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderReadRepository read;

    @Autowired
    CustomerRepository customers;

    @Autowired
    CatalogPlanRepository plans;

    @Autowired
    IdGenerator ids;

    private Order existingOrder() {
        String email = "order-" + UUID.randomUUID() + "@example.com";
        User user = registerUser.execute(
                new RegisterUserUseCase.Command(email, "Ada", HttpWorld.PASSWORD, Role.VIEWER));
        Customer customer = customers.findByUserId(user.id()).orElseThrow();
        CatalogPlan plan = new CatalogPlan(
                UUID.randomUUID(), "order-" + UUID.randomUUID(), "Compute",
                ResourceType.COMPUTE, "description", "bplan_1", true, NOW);
        plans.save(plan);
        Order order = Order.queued(
                ids, customer.id(), customer.billingCustomerId(), "wal_1", plan.id(), "bplan_1",
                "TIME_BASED", "compute.vcpu_minute", "120", "vCPU-minute", null, 1000, "USD", null, null, NOW);
        orders.save(order);
        return order;
    }

    @Test
    void roundTripsAndUpdatesTheStatus() {
        Order order = existingOrder();

        Order loaded = orders.findById(order.id()).orElseThrow();
        assertThat(loaded.reference()).isEqualTo(order.reference());
        assertThat(loaded.status()).isEqualTo(OrderStatus.QUEUED);
        assertThat(orders.findByBillingCustomerId(order.billingCustomerId())).hasSize(1);

        orders.save(loaded.canceled(NOW));

        assertThat(orders.findById(order.id()).orElseThrow().status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void theReadSideSelectsIntoViews() {
        Order order = existingOrder();

        assertThat(read.byId(order.id()).orElseThrow().status()).isEqualTo("QUEUED");
        assertThat(read.forCustomer(order.customerId())).extracting(view -> view.id()).contains(order.id());
        assertThat(read.forBillingCustomer(order.billingCustomerId())).extracting(view -> view.id()).contains(order.id());
        assertThat(read.all()).extracting(view -> view.id()).contains(order.id());
    }
}
