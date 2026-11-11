package dev.ngb.core_billing.pricing.adapter.plan.web;

import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.pricing.adapter.plan.json.RuleDto;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.pricing.application.plan.command.ManagePlanUseCase;
import dev.ngb.core_billing.pricing.application.plan.query.PlanQueries;
import dev.ngb.core_billing.pricing.application.plan.query.PlanVersionView;
import dev.ngb.core_billing.pricing.application.plan.query.PricingPlanView;
import dev.ngb.core_billing.pricing.domain.plan.PlanId;
import dev.ngb.core_billing.pricing.domain.plan.PlanStatus;
import dev.ngb.core_billing.pricing.domain.plan.PlanVersion;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlan;
import dev.ngb.core_billing.pricing.domain.plan.RoundingPolicy;
import dev.ngb.core_billing.pricing.domain.rule.PricingModel;
import dev.ngb.core_billing.sharedkernel.Currency;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Pricing plans and their versions. Prices are decimals in minor units of the plan's currency. */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class PlanController {

    public record VersionRequest(
            RuleDto rule, String billingCycle, Long minCharge, Long maxCharge, String rounding, Instant validFrom, Instant validUntil) {}

    public record CreatePlanRequest(String code, String name, String currency, String model, VersionRequest version) {}

    public record VersionResponse(
            int version, RuleDto rule, String billingCycle, Long minCharge, Long maxCharge, String rounding, Instant validFrom, Instant validUntil,
            Instant createdAt, String createdBy) {
        static VersionResponse of(PlanVersion v) {
            return new VersionResponse(v.number(), RuleDto.of(v.rule()), v.billingCycle() == null ? null : v.billingCycle().name(), v.minCharge(), v.maxCharge(),
                    v.rounding().name(), v.validFrom(), v.validUntil(), v.createdAt(), v.createdBy());
        }

        static VersionResponse ofView(PlanVersionView v, JsonMapper json) {
            return new VersionResponse(v.version(), json.readValue(v.rule(), RuleDto.class),
                    v.billingCycle() == null ? null : v.billingCycle().name(), v.minCharge(), v.maxCharge(), v.rounding().name(),
                    v.validFrom(), v.validUntil(), v.createdAt(), v.createdBy());
        }
    }

    public record PlanResponse(
            String id, String code, String name, String model, String currency, String status, Instant createdAt, Instant retiredAt, List<VersionResponse> versions) {
        static PlanResponse of(PricingPlan p) {
            return new PlanResponse(p.id().toString(), p.code(), p.name(), p.model().name(), p.currency().code(), p.status().name(), p.createdAt(), p.retiredAt(),
                    p.versions().stream().map(VersionResponse::of).toList());
        }

        static PlanResponse ofView(PricingPlanView p, JsonMapper json) {
            return new PlanResponse(p.id().toString(), p.code(), p.name(), p.model().name(), p.currency(), p.status().name(), p.createdAt(), p.retiredAt(),
                    p.versions().stream().map(v -> VersionResponse.ofView(v, json)).toList());
        }
    }

    private final ManagePlanUseCase plans;
    private final PlanQueries planQueries;
    private final CurrentActor currentActor;
    private final JsonMapper json;

    public PlanController(ManagePlanUseCase plans, PlanQueries planQueries, CurrentActor currentActor, JsonMapper json) {
        this.plans = plans;
        this.planQueries = planQueries;
        this.currentActor = currentActor;
        this.json = json;
    }

    @PostMapping("/pricing-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.PRICING_MANAGE)
    public PlanResponse createPlan(@RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody CreatePlanRequest request) {
        if (request.version() == null || request.version().rule() == null || request.model() == null) {
            throw new IllegalArgumentException("model and version.rule are required");
        }
        PricingModel model = PricingModel.valueOf(request.model());
        return PlanResponse.of(plans.create(Audit.of(currentActor.get(), requestId), request.code(), request.name(), Currency.of(required(request.currency(), "currency")),
                terms(request.version(), model)));
    }

    /** Tenant systems list the plans they may offer: pass {@code status=ACTIVE}. */
    @GetMapping("/pricing-plans")
    @RequiresPermission(Permission.PRICING_READ)
    public List<PlanResponse> listPlans(@RequestParam(required = false) String status) {
        return planQueries.list(status == null ? null : PlanStatus.valueOf(status)).stream().map(v -> PlanResponse.ofView(v, json)).toList();
    }

    @GetMapping("/pricing-plans/{id}")
    @RequiresPermission(Permission.PRICING_READ)
    public PlanResponse getPlan(@PathVariable String id) {
        return PlanResponse.ofView(planQueries.get(UUID.fromString(id)), json);
    }

    /** Adds the next version. Earlier versions, and every charge that used them, are unchanged. */
    @PostMapping("/pricing-plans/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.PRICING_MANAGE)
    public VersionResponse publishVersion(
            @PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody VersionRequest request) {
        if (request.rule() == null) {
            throw new IllegalArgumentException("rule is required");
        }
        PlanId planId = PlanId.of(id);
        return VersionResponse.of(plans.publishVersion(Audit.of(currentActor.get(), requestId), planId, terms(request, plans.get(planId).model())));
    }

    @PostMapping("/pricing-plans/{id}/retire")
    @RequiresPermission(Permission.PRICING_MANAGE)
    public PlanResponse retire(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return PlanResponse.of(plans.retire(Audit.of(currentActor.get(), requestId), PlanId.of(id)));
    }

    private static ManagePlanUseCase.Terms terms(VersionRequest v, PricingModel model) {
        return new ManagePlanUseCase.Terms(v.rule().toRule(model), v.billingCycle() == null ? null : BillingCycle.valueOf(v.billingCycle()), v.minCharge(),
                v.maxCharge(), v.rounding() == null ? null : RoundingPolicy.valueOf(v.rounding()), v.validFrom(), v.validUntil());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
