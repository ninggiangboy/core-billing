package dev.ngb.tenant.catalog.application.plan.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.tenant.catalog.application.plan.command.AttachBillingPlanUseCase;
import dev.ngb.tenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.tenant.catalog.application.plan.service.CatalogLookupService;
import dev.ngb.tenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.tenant.catalog.domain.plan.ResourceType;
import dev.ngb.tenant.shared.error.NotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogQueriesTest {

    private final InMemoryPlans plans = new InMemoryPlans();
    private final CatalogQueries queries = new CatalogQueries(plans);
    private final CatalogLookupService lookup = new CatalogLookupService(plans);
    private final AttachBillingPlanUseCase attach = new AttachBillingPlanUseCase(plans);

    @Test
    void listsOnlyActivePlans() {
        plans.save(plan("compute-standard", ResourceType.COMPUTE, true, null));
        plans.save(plan("storage-standard", ResourceType.STORAGE, false, null));

        assertThat(queries.listActive()).extracting(PlanView::code).containsExactly("compute-standard");
    }

    @Test
    void bindsABillingPlanByCode() {
        CatalogPlan plan = plan("compute-standard", ResourceType.COMPUTE, true, null);
        plans.save(plan);

        CatalogPlan bound = attach.attachByCode("compute-standard", "bplan_1");

        assertThat(bound.billingPlanId()).isEqualTo("bplan_1");
        assertThat(lookup.get(plan.id()).billable()).isTrue();
    }

    @Test
    void refusesAnUnknownCode() {
        assertThatThrownBy(() -> attach.attachByCode("missing", "bplan_1")).isInstanceOf(NotFoundException.class);
    }

    private static CatalogPlan plan(String code, ResourceType type, boolean active, String billingPlanId) {
        return new CatalogPlan(
                UUID.randomUUID(), code, type.label(), type, "description", billingPlanId, active, Instant.parse("2026-11-11T00:00:00Z"));
    }

    private static final class InMemoryPlans implements CatalogPlanRepository, CatalogPlanReadRepository {
        private final Map<UUID, CatalogPlan> byId = new LinkedHashMap<>();

        @Override
        public void save(CatalogPlan plan) {
            byId.put(plan.id(), plan);
        }

        @Override
        public Optional<CatalogPlan> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<CatalogPlan> findByCode(String code) {
            return byId.values().stream().filter(plan -> plan.code().equals(code)).findFirst();
        }

        @Override
        public List<PlanView> listActive() {
            return all().stream().filter(PlanView::active).toList();
        }

        @Override
        public List<PlanView> all() {
            return byId.values().stream().map(InMemoryPlans::view).toList();
        }

        @Override
        public Optional<PlanView> byId(UUID id) {
            return Optional.ofNullable(byId.get(id)).map(InMemoryPlans::view);
        }

        @Override
        public Optional<PlanView> byCode(String code) {
            return findByCode(code).map(InMemoryPlans::view);
        }

        private static PlanView view(CatalogPlan plan) {
            return new PlanView(
                    plan.id(), plan.code(), plan.name(), plan.resourceType(), plan.description(),
                    plan.billingPlanId(), plan.active(), plan.createdAt());
        }
    }
}
