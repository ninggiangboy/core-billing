package dev.ngb.sampletenant.boot.bootstrap;

import dev.ngb.sampletenant.billing.api.BillingAdmin;
import dev.ngb.sampletenant.billing.api.model.Rule;
import dev.ngb.sampletenant.billing.api.model.Tier;
import dev.ngb.sampletenant.catalog.domain.CatalogPlan;
import java.util.List;

/** The reference rules the bootstrap publishes for the seven catalog plans. USD, monthly, half-up. */
final class ReferencePlans {

    private ReferencePlans() {}

    static BillingAdmin.CreatePlanRequest request(CatalogPlan plan) {
        Rule rule = switch (plan.resourceType()) {
            case COMPUTE -> usageRule(plan, "0.02", "1", null);
            case STORAGE -> tiersRule(plan);
            case BANDWIDTH -> usageRule(plan, "0.05", null, null);
            case BACKGROUND_WORKLOAD -> workloadRule(plan);
            case API_OPERATION -> usageRule(plan, "0.0001", null, null);
            case MANAGED_SERVICE -> subscriptionRule(plan);
            case CLOUD_PACKAGE -> hybridRule(plan);
        };
        return new BillingAdmin.CreatePlanRequest(
                plan.code(),
                plan.name(),
                "USD",
                plan.resourceType().pricingModel().name(),
                new BillingAdmin.VersionRequest(rule, "MONTHLY", null, null, "HALF_UP", null, null));
    }

    private static Rule usageRule(CatalogPlan plan, String unitPrice, String increment, String fixedPrice) {
        return new Rule(
                plan.resourceType().pricingModel().name(), plan.resourceType().metric(), plan.resourceType().unit(),
                unitPrice, increment, null, List.of(), null, fixedPrice, null);
    }

    private static Rule tiersRule(CatalogPlan plan) {
        return new Rule(
                "USAGE_BASED", plan.resourceType().metric(), plan.resourceType().unit(), null, null, "GRADUATED",
                List.of(new Tier("100", "0"), new Tier(null, "0.01")), null, null, null);
    }

    private static Rule workloadRule(CatalogPlan plan) {
        return new Rule(
                "WORKLOAD_BASED", plan.resourceType().metric(), plan.resourceType().unit(), "0.01", null, null,
                List.of(), "0.001", null, null);
    }

    private static Rule subscriptionRule(CatalogPlan plan) {
        return new Rule(
                "SUBSCRIPTION", null, plan.resourceType().unit(), null, null, null, List.of(), null, "1000", null);
    }

    private static Rule hybridRule(CatalogPlan plan) {
        Rule usage = new Rule(
                "USAGE_BASED", plan.resourceType().metric(), plan.resourceType().unit(), "0.01", null, null,
                List.of(), null, null, null);
        return new Rule("HYBRID", null, plan.resourceType().unit(), null, null, null, List.of(), null, "500", List.of(usage));
    }
}
