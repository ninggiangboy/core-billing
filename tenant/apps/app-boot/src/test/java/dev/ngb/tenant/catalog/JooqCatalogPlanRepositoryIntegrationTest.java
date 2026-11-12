package dev.ngb.tenant.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.tenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.tenant.catalog.application.plan.query.CatalogPlanReadRepository;
import dev.ngb.tenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.tenant.catalog.domain.plan.ResourceType;
import dev.ngb.tenant.support.HttpIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqCatalogPlanRepositoryIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    CatalogPlanRepository plans;

    @Autowired
    CatalogPlanReadRepository read;

    @Test
    void roundTripsAndUpsertsAPlan() {
        CatalogPlan plan = plan(true, null);

        plans.save(plan);

        CatalogPlan loaded = plans.findByCode(plan.code()).orElseThrow();
        assertThat(loaded.id()).isEqualTo(plan.id());
        assertThat(loaded.resourceType()).isEqualTo(ResourceType.COMPUTE);
        assertThat(loaded.billable()).isFalse();

        plans.save(plan.withBillingPlan("bplan_1"));

        assertThat(plans.findByCode(plan.code()).orElseThrow().billingPlanId()).isEqualTo("bplan_1");
        assertThat(plans.findById(plan.id()).orElseThrow().billable()).isTrue();
    }

    @Test
    void savingAnExistingPlanOnlyChangesItsBillingBindingAndActiveFlag() {
        CatalogPlan plan = plan(true, null);
        plans.save(plan);

        plans.save(new CatalogPlan(
                plan.id(), "renamed-" + plan.code(), "Renamed", ResourceType.STORAGE, "other", "bplan_9", false, NOW.plusSeconds(1)));

        CatalogPlan loaded = plans.findById(plan.id()).orElseThrow();
        assertThat(loaded.billingPlanId()).isEqualTo("bplan_9");
        assertThat(loaded.active()).isFalse();
        assertThat(loaded.code()).isEqualTo(plan.code());
        assertThat(loaded.name()).isEqualTo("Compute");
        assertThat(loaded.resourceType()).isEqualTo(ResourceType.COMPUTE);
        assertThat(loaded.description()).isEqualTo("description");
        assertThat(loaded.createdAt()).isEqualTo(NOW);
    }

    @Test
    void theReadSideSelectsIntoViews() {
        CatalogPlan active = plan(true, "bplan_1");
        CatalogPlan inactive = plan(false, null);
        plans.save(active);
        plans.save(inactive);

        assertThat(read.byCode(active.code()).orElseThrow().resourceType()).isEqualTo(ResourceType.COMPUTE);
        assertThat(read.byId(active.id()).orElseThrow().billable()).isTrue();
        assertThat(read.all()).extracting(view -> view.code()).contains(active.code(), inactive.code());
        assertThat(read.listActive()).extracting(view -> view.code())
                .contains(active.code())
                .doesNotContain(inactive.code());
    }

    private static CatalogPlan plan(boolean active, String billingPlanId) {
        return new CatalogPlan(
                UUID.randomUUID(), "catalog-" + UUID.randomUUID(), "Compute",
                ResourceType.COMPUTE, "description", billingPlanId, active, NOW);
    }
}
