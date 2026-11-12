package dev.ngb.sampletenant.catalog.adapter.plan.web;

import dev.ngb.sampletenant.catalog.application.plan.query.CatalogQueries;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The offer catalog the ordering screens read. */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    public record PlanResponse(
            UUID id,
            String code,
            String name,
            String resourceType,
            String resourceLabel,
            String pricingModel,
            String metric,
            String unit,
            String billingPlanId,
            boolean active) {
        static PlanResponse of(CatalogPlan plan) {
            ResourceType type = plan.resourceType();
            return new PlanResponse(
                    plan.id(),
                    plan.code(),
                    plan.name(),
                    type.name(),
                    type.label(),
                    type.pricingModel().name(),
                    type.metric(),
                    type.unit(),
                    plan.billingPlanId(),
                    plan.active());
        }
    }

    public record ResourceTypeResponse(String code, String label, String pricingModel, String metric, String unit) {
        static ResourceTypeResponse of(ResourceType type) {
            return new ResourceTypeResponse(
                    type.name(), type.label(), type.pricingModel().name(), type.metric(), type.unit());
        }
    }

    private final CatalogQueries queries;

    public CatalogController(CatalogQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return queries.listActive().stream().map(PlanResponse::of).toList();
    }

    @GetMapping("/plans/{id}")
    public PlanResponse plan(@PathVariable UUID id) {
        return PlanResponse.of(queries.plan(id));
    }

    @GetMapping("/resource-types")
    public List<ResourceTypeResponse> resourceTypes() {
        return Arrays.stream(ResourceType.values()).map(ResourceTypeResponse::of).toList();
    }
}
