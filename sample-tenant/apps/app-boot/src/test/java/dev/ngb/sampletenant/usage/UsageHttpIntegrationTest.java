package dev.ngb.sampletenant.usage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.resource.application.order.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.order.Order;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import dev.ngb.sampletenant.usage.application.usage.port.UsageRepository;
import dev.ngb.sampletenant.usage.domain.usage.UsageRecord;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

class UsageHttpIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    UsageRepository usage;

    @Autowired
    OrderRepository orders;

    @Autowired
    CatalogPlanRepository plans;

    @Autowired
    IdGenerator ids;

    @Test
    void listsTheCurrentCustomersUsageWithNoRecordsYet() {
        String viewer = world.token(Role.VIEWER);

        var usage = world.body(world.call(HttpMethod.GET, viewer, "/api/usage", null));

        assertThat(usage.isArray()).isTrue();
        assertThat(usage.size()).isZero();
    }

    @Test
    void returnsEveryFieldOfTheRecordsNewestFirst() {
        HttpWorld.Account account = world.registerViaApi();
        Mapped customer = mapped(account);
        UUID orderId = orderFor(customer);
        Instant earlier = NOW.plusSeconds(60);
        Instant later = NOW.plusSeconds(120);
        UsageRecord first = record(orderId, customer.billingId(), "compute.vcpu_minute", "120", "vCPU-minute", "use_1", earlier);
        UsageRecord second = record(orderId, customer.billingId(), "storage.gb_hour", "5", "GB-hour", "use_2", later);
        usage.save(first);
        usage.save(second);

        JsonNode body = world.body(world.call(HttpMethod.GET, account.token(), "/api/usage", null));

        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get(0).get("id").asString()).isEqualTo(second.id().toString());
        assertThat(body.get(0).get("orderId").asString()).isEqualTo(orderId.toString());
        assertThat(body.get(0).get("metric").asString()).isEqualTo("storage.gb_hour");
        assertThat(body.get(0).get("quantity").asString()).isEqualTo("5");
        assertThat(body.get(0).get("unit").asString()).isEqualTo("GB-hour");
        assertThat(body.get(0).get("billingEventId").asString()).isEqualTo("use_2");
        assertThat(Instant.parse(body.get(0).get("occurredAt").asString())).isEqualTo(later);
        assertThat(Instant.parse(body.get(0).get("recordedAt").asString())).isEqualTo(NOW);
        assertThat(body.get(1).get("id").asString()).isEqualTo(first.id().toString());
        assertThat(body.get(1).get("metric").asString()).isEqualTo("compute.vcpu_minute");
        assertThat(Instant.parse(body.get(1).get("occurredAt").asString())).isEqualTo(earlier);
    }

    @Test
    void aCustomerNeverSeesAnotherCustomersUsage() {
        HttpWorld.Account owner = world.registerViaApi();
        HttpWorld.Account other = world.registerViaApi();
        Mapped customer = mapped(owner);
        usage.save(record(orderFor(customer), customer.billingId(), "compute.vcpu_minute", "1", "vCPU-minute", "use_x", NOW));

        JsonNode otherBody = world.body(world.call(HttpMethod.GET, other.token(), "/api/usage", null));
        JsonNode ownerBody = world.body(world.call(HttpMethod.GET, owner.token(), "/api/usage", null));

        assertThat(otherBody.size()).isZero();
        assertThat(ownerBody.size()).isEqualTo(1);
    }

    @Test
    void requiresAuthentication() {
        world.requireStatus(world.call(HttpMethod.GET, null, "/api/usage", null), 401);
    }

    private record Mapped(UUID id, String billingId) {}

    private Mapped mapped(HttpWorld.Account account) {
        JsonNode me = world.body(world.call(HttpMethod.GET, account.token(), "/api/customers/me", null));
        return new Mapped(UUID.fromString(me.get("id").asString()), me.get("billingCustomerId").asString());
    }

    private UUID orderFor(Mapped customer) {
        CatalogPlan plan = new CatalogPlan(
                UUID.randomUUID(), "usage-http-" + UUID.randomUUID(), "Compute",
                ResourceType.COMPUTE, "description", "bplan_1", true, NOW);
        plans.save(plan);
        Order order = Order.queued(
                ids, customer.id(), customer.billingId(), "wal_test", plan.id(), "bplan_1",
                "TIME_BASED", "compute.vcpu_minute", "120", "vCPU-minute", null, 1000, "USD", null, null, NOW);
        orders.save(order);
        return order.id();
    }

    private UsageRecord record(
            UUID orderId, String billingCustomerId, String metric, String quantity, String unit, String eventId, Instant occurredAt) {
        return UsageRecord.recorded(
                ids, orderId, billingCustomerId, metric, quantity, unit, eventId, "usage-http:" + UUID.randomUUID(), occurredAt, NOW);
    }
}
