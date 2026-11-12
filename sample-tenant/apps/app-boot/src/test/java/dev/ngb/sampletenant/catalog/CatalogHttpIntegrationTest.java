package dev.ngb.sampletenant.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class CatalogHttpIntegrationTest extends HttpIntegrationTest {

    @Autowired
    CatalogPlanRepository plans;

    @Test
    void servesTheCatalog() {
        String viewer = world.token(Role.VIEWER);
        CatalogPlan plan = new CatalogPlan(
                UUID.randomUUID(), "compute-" + UUID.randomUUID(), "Compute",
                ResourceType.COMPUTE, "description", "bplan_1", true, Instant.parse("2026-11-11T00:00:00Z"));
        plans.save(plan);

        var list = world.body(world.call(HttpMethod.GET, viewer, "/api/catalog/plans", null));
        assertThat(list.isArray()).isTrue();
        assertThat(list.findValuesAsString("code")).contains(plan.code());

        var byId = world.body(world.call(HttpMethod.GET, viewer, "/api/catalog/plans/" + plan.id(), null));
        assertThat(byId.get("id").asString()).isEqualTo(plan.id().toString());
        assertThat(byId.get("code").asString()).isEqualTo(plan.code());
        assertThat(byId.get("name").asString()).isEqualTo("Compute");
        assertThat(byId.get("resourceType").asString()).isEqualTo("COMPUTE");
        assertThat(byId.get("resourceLabel").asString()).isNotBlank();
        assertThat(byId.get("pricingModel").asString()).isEqualTo("TIME_BASED");
        assertThat(byId.get("metric").asString()).isEqualTo("compute.vcpu_minute");
        assertThat(byId.get("unit").asString()).isEqualTo("vCPU-minute");
        assertThat(byId.get("billingPlanId").asString()).isEqualTo("bplan_1");
        assertThat(byId.get("active").asBoolean()).isTrue();
    }

    @Test
    void listsEveryResourceType() {
        String viewer = world.token(Role.VIEWER);

        var types = world.body(world.call(HttpMethod.GET, viewer, "/api/catalog/resource-types", null));

        assertThat(types.isArray()).isTrue();
        assertThat(types.size()).isEqualTo(7);
        assertThat(types.get(0).get("code").asString()).isNotBlank();
        assertThat(types.get(0).get("label").asString()).isNotBlank();
        assertThat(types.get(0).get("pricingModel").asString()).isNotBlank();
    }

    @Test
    void anUnknownPlanIsNotFound() {
        String viewer = world.token(Role.VIEWER);

        world.requireStatus(world.call(HttpMethod.GET, viewer, "/api/catalog/plans/" + UUID.randomUUID(), null), 404);
    }
}
