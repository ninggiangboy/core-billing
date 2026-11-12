package dev.ngb.sampletenant.resource;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class OrderHttpIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    CatalogPlanRepository plans;

    private CatalogPlan billablePlan() {
        CatalogPlan plan = new CatalogPlan(
                UUID.randomUUID(), "compute-" + UUID.randomUUID(), "Compute",
                ResourceType.COMPUTE, "description", "bplan_1", true, NOW);
        plans.save(plan);
        return plan;
    }

    @Test
    void createsListsReadsAndCancelsAnOrder() {
        String operator = world.token(Role.OPERATOR);
        CatalogPlan plan = billablePlan();

        MvcResult created = world.call(HttpMethod.POST, operator, "/api/orders",
                HttpWorld.map("planId", plan.id(), "quantity", "120", "events", null));
        world.requireStatus(created, 201);
        var order = world.parse(created);
        String id = order.get("id").asString();
        assertThat(id).isNotBlank();
        assertThat(order.get("reference").asString()).startsWith("ORD-");
        assertThat(order.get("planId").asString()).isEqualTo(plan.id().toString());
        assertThat(order.get("billingPlanId").asString()).isEqualTo("bplan_1");
        assertThat(order.get("status").asString()).isEqualTo("QUEUED");
        assertThat(order.get("walletId").asString()).startsWith("wal_");
        assertThat(order.get("reservationId").asString()).isEqualTo("res_1");
        assertThat(order.get("estimatedMinorUnits").asLong()).isEqualTo(1000L);
        assertThat(order.get("currency").asString()).isEqualTo("USD");
        assertThat(order.get("failureReason").isNull()).isTrue();
        assertThat(order.get("createdAt").asString()).isNotBlank();
        assertThat(order.get("updatedAt").asString()).isNotBlank();

        var list = world.body(world.call(HttpMethod.GET, operator, "/api/orders", null));
        assertThat(list.isArray()).isTrue();
        assertThat(list.findValuesAsString("id")).contains(id);

        var byId = world.body(world.call(HttpMethod.GET, operator, "/api/orders/" + id, null));
        assertThat(byId.get("id").asString()).isEqualTo(id);

        var canceled = world.body(world.call(HttpMethod.POST, operator, "/api/orders/" + id + "/cancel", null));
        assertThat(canceled.get("status").asString()).isEqualTo("CANCELED");
    }

    @Test
    void refusesAnUnboundPlan() {
        String operator = world.token(Role.OPERATOR);
        CatalogPlan plan = new CatalogPlan(
                UUID.randomUUID(), "unbound-" + UUID.randomUUID(), "Unbound",
                ResourceType.COMPUTE, "description", null, true, NOW);
        plans.save(plan);

        MvcResult result = world.call(HttpMethod.POST, operator, "/api/orders",
                HttpWorld.map("planId", plan.id(), "quantity", "1", "events", null));

        world.requireStatus(result, 400);
        assertThat(world.parse(result).get("detail").asString()).isEqualTo("this plan is not bound to a Billing plan yet");
    }

    @Test
    void refusesAReservationTheWalletCannotCover() {
        String operator = world.token(Role.OPERATOR);
        CatalogPlan plan = billablePlan();
        billing.refuseReservation();
        try {
            MvcResult result = world.call(HttpMethod.POST, operator, "/api/orders",
                    HttpWorld.map("planId", plan.id(), "quantity", "120", "events", null));

            world.requireStatus(result, 402);
            assertThat(world.parse(result).get("detail").asString()).isEqualTo("The wallet cannot cover this amount.");
        } finally {
            billing.acceptReservation();
        }
    }

    @Test
    void anUnknownPlanOrOrderIsNotFound() {
        String operator = world.token(Role.OPERATOR);

        world.requireStatus(world.call(HttpMethod.POST, operator, "/api/orders",
                HttpWorld.map("planId", UUID.randomUUID(), "quantity", "1", "events", null)), 404);
        world.requireStatus(world.call(HttpMethod.GET, operator, "/api/orders/" + UUID.randomUUID(), null), 404);
    }
}
