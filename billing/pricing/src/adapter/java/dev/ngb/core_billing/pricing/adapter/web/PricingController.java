package dev.ngb.core_billing.pricing.adapter.web;

import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.pricing.adapter.persistence.RuleDto;
import dev.ngb.core_billing.pricing.application.shared.command.Audit;
import dev.ngb.core_billing.pricing.application.rating.command.ChargeFromUsageUseCase;
import dev.ngb.core_billing.pricing.application.rating.command.EstimateCostUseCase;
import dev.ngb.core_billing.pricing.application.plan.command.PlanAdministration;
import dev.ngb.core_billing.pricing.application.plan.query.PlanQueries;
import dev.ngb.core_billing.pricing.application.plan.query.PlanVersionView;
import dev.ngb.core_billing.pricing.application.plan.query.PricingPlanView;
import dev.ngb.core_billing.pricing.application.rating.query.PriceLineView;
import dev.ngb.core_billing.pricing.application.rating.query.RatingQueries;
import dev.ngb.core_billing.pricing.application.rating.query.UsageRatingView;
import dev.ngb.core_billing.pricing.application.rating.command.ReserveFromEstimateUseCase;
import dev.ngb.core_billing.pricing.domain.plan.BillingCycle;
import dev.ngb.core_billing.pricing.domain.metric.Measure;
import dev.ngb.core_billing.pricing.domain.plan.PlanId;
import dev.ngb.core_billing.pricing.domain.plan.PlanStatus;
import dev.ngb.core_billing.pricing.domain.plan.PlanVersion;
import dev.ngb.core_billing.pricing.domain.plan.PriceLine;
import dev.ngb.core_billing.pricing.domain.model.PricingModel;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlan;
import dev.ngb.core_billing.pricing.domain.rating.RatingStatus;
import dev.ngb.core_billing.pricing.domain.plan.RoundingPolicy;
import dev.ngb.core_billing.pricing.domain.rating.UsageRating;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * Pricing plans and what is done with them: cost estimation, reserving the estimate, and charging usage.
 * Prices are decimals in minor units of the plan's currency; amounts that are charged are integers in minor units.
 */
@RestController
@RequestMapping("/api/v1")
public class PricingController {

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

    public record AmountDto(long amount, String currency) {
        static AmountDto of(Money money) {
            return new AmountDto(money.minorUnits(), money.currency().code());
        }
    }

    /** Expected usage of one metric; {@code events} defaults to 1 and only matters to workload prices. */
    public record UsageInput(String metric, String quantity, Long events) {}

    public record EstimateRequest(String planId, Integer version, Instant at, List<UsageInput> usage) {}

    public record LineResponse(String description, String metric, String quantity, String amount) {
        static LineResponse of(PriceLine line) {
            return new LineResponse(line.description(), line.metric(), line.quantity() == null ? null : line.quantity().stripTrailingZeros().toPlainString(),
                    line.amount().stripTrailingZeros().toPlainString());
        }

        static LineResponse ofView(PriceLineView line) {
            return new LineResponse(line.description(), line.metric(), line.quantity() == null ? null : line.quantity().stripTrailingZeros().toPlainString(),
                    line.amount().stripTrailingZeros().toPlainString());
        }
    }

    public record EstimateResponse(
            String planId, String planCode, int planVersion, AmountDto amount, String exactAmount, String adjustment, List<LineResponse> lines) {
        static EstimateResponse of(EstimateCostUseCase.Estimate e) {
            return new EstimateResponse(e.plan().id().toString(), e.plan().code(), e.version().number(), AmountDto.of(e.quote().amount()),
                    e.quote().exactAmount().stripTrailingZeros().toPlainString(), e.quote().adjustment().name(),
                    e.quote().lines().stream().map(LineResponse::of).toList());
        }
    }

    public record ReserveRequest(String walletId, String planId, Integer version, Instant at, List<UsageInput> usage, Long ttlSeconds, String reference) {}

    public record ReservationDto(String id, String status, AmountDto amount, Instant expiresAt, boolean replayed) {
        static ReservationDto of(ReservationResult r) {
            return new ReservationDto(r.id().toString(), r.status().name(), AmountDto.of(r.amount()), r.expiresAt(), r.replayed());
        }
    }

    public record ReserveResponse(EstimateResponse estimate, ReservationDto reservation) {}

    public record ChargeUsageRequest(String customerId, String walletId, String planId, Integer version, Instant from, Instant to) {}

    public record RatingResponse(
            String outcome, String id, String planId, Integer planVersion, AmountDto amount, String chargeId, String failureReason, Integer usageCount,
            Instant periodStart, Instant periodEnd, List<LineResponse> lines, boolean replayed) {
        static RatingResponse of(ChargeFromUsageUseCase.Result result) {
            UsageRating r = result.rating();
            if (r == null) {
                return new RatingResponse(result.outcome().name(), null, null, null, null, null, null, 0, null, null, List.of(), false);
            }
            return new RatingResponse(result.outcome().name(), r.id().toString(), r.planId().toString(), r.planVersion(), AmountDto.of(r.amount()),
                    r.chargeId() == null ? null : r.chargeId().toString(), r.failureReason(), r.usageCount(), r.periodStart(), r.periodEnd(),
                    r.breakdown().stream().map(LineResponse::of).toList(), result.replayed());
        }

        static RatingResponse ofView(UsageRatingView r) {
            return new RatingResponse(outcome(r.status()), r.id().toString(), r.planId().toString(), r.planVersion(), AmountDto.of(r.amount()),
                    r.chargeId() == null ? null : r.chargeId().toString(), r.failureReason(), r.usageCount(), r.periodStart(), r.periodEnd(),
                    r.breakdown().stream().map(LineResponse::ofView).toList(), false);
        }

        private static String outcome(RatingStatus status) {
            return switch (status) {
                case CHARGED -> "CHARGED";
                case FAILED -> "FAILED";
                case NO_CHARGE -> "NO_CHARGE";
            };
        }
    }

    private final PlanAdministration plans;
    private final PlanQueries planQueries;
    private final EstimateCostUseCase estimates;
    private final ReserveFromEstimateUseCase reserve;
    private final ChargeFromUsageUseCase chargeUsage;
    private final RatingQueries ratings;
    private final CurrentActor currentActor;
    private final JsonMapper json;

    public PricingController(
            PlanAdministration plans, PlanQueries planQueries, EstimateCostUseCase estimates, ReserveFromEstimateUseCase reserve,
            ChargeFromUsageUseCase chargeUsage, RatingQueries ratings, CurrentActor currentActor, JsonMapper json) {
        this.plans = plans;
        this.planQueries = planQueries;
        this.estimates = estimates;
        this.reserve = reserve;
        this.chargeUsage = chargeUsage;
        this.ratings = ratings;
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
        return PlanResponse.of(plans.create(audit(requestId), request.code(), request.name(), Currency.of(required(request.currency(), "currency")),
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
        return VersionResponse.of(plans.publishVersion(audit(requestId), planId, terms(request, plans.get(planId).model())));
    }

    @PostMapping("/pricing-plans/{id}/retire")
    @RequiresPermission(Permission.PRICING_MANAGE)
    public PlanResponse retire(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return PlanResponse.of(plans.retire(audit(requestId), PlanId.of(id)));
    }

    /** A pure calculation: nothing is stored, and the same input is priced exactly as the real charge would be. */
    @PostMapping("/pricing/estimates")
    @RequiresPermission(Permission.PRICING_READ)
    public EstimateResponse estimate(@RequestBody EstimateRequest request) {
        return EstimateResponse.of(estimates.estimate(new EstimateCostUseCase.Request(PlanId.of(required(request.planId(), "planId")), request.version(), request.at(), measures(request.usage()))));
    }

    /** Holds the estimated cost on the wallet. 201 on success, 402 when the wallet cannot cover it. */
    @PostMapping("/pricing/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.RESERVATION_MANAGE)
    public ReserveResponse reserveEstimate(@RequestHeader("Idempotency-Key") String key, @RequestBody ReserveRequest request) {
        ReserveFromEstimateUseCase.Result result = reserve.reserve(new ReserveFromEstimateUseCase.Command(
                WalletId.of(required(request.walletId(), "walletId")), PlanId.of(required(request.planId(), "planId")), request.version(), request.at(),
                measures(request.usage()),
                Duration.ofSeconds(request.ttlSeconds() == null ? 900 : request.ttlSeconds()), request.reference(), key, caller()));
        return new ReserveResponse(EstimateResponse.of(result.estimate()), ReservationDto.of(result.reservation()));
    }

    /**
     * Prices the customer's not-yet-rated usage in {@code [from, to)} and charges the wallet. Repeating the request
     * returns the earlier result instead of charging again. 201 when charged, 200 when repeated or when there was
     * nothing to charge, 402 when the wallet could not pay (the same request succeeds after a top-up).
     */
    @PostMapping("/pricing/usage-charges")
    @RequiresPermission(Permission.CHARGE_CREATE)
    public ResponseEntity<RatingResponse> chargeUsage(@RequestBody ChargeUsageRequest request) {
        if (request.from() == null || request.to() == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        ChargeFromUsageUseCase.Result result = chargeUsage.execute(new ChargeFromUsageUseCase.Command(
                CustomerId.of(required(request.customerId(), "customerId")), WalletId.of(required(request.walletId(), "walletId")),
                PlanId.of(required(request.planId(), "planId")), request.version(), request.from(), request.to(), caller()));
        HttpStatus status = switch (result.outcome()) {
            case CHARGED -> result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
            case FAILED -> HttpStatus.PAYMENT_REQUIRED;
            case NO_CHARGE, NO_USAGE -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(RatingResponse.of(result));
    }

    @GetMapping("/pricing/usage-charges/{id}")
    @RequiresPermission(Permission.PRICING_READ)
    public RatingResponse getRating(@PathVariable String id) {
        return RatingResponse.ofView(ratings.get(UUID.fromString(id), caller()));
    }

    private java.util.UUID caller() {
        var system = currentActor.get().tenantSystemId();
        return system == null ? null : system.value();
    }

    private static PlanAdministration.Terms terms(VersionRequest v, PricingModel model) {
        return new PlanAdministration.Terms(v.rule().toRule(model), v.billingCycle() == null ? null : BillingCycle.valueOf(v.billingCycle()), v.minCharge(),
                v.maxCharge(), v.rounding() == null ? null : RoundingPolicy.valueOf(v.rounding()), v.validFrom(), v.validUntil());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static Map<String, Measure> measures(List<UsageInput> inputs) {
        Map<String, Measure> result = new HashMap<>();
        if (inputs == null) {
            return result;
        }
        for (UsageInput input : inputs) {
            if (input.metric() == null || input.quantity() == null) {
                throw new IllegalArgumentException("each usage entry needs a metric and a quantity");
            }
            BigDecimal quantity = new BigDecimal(input.quantity().strip());
            long events = input.events() != null ? input.events() : quantity.signum() > 0 ? 1 : 0;
            Measure added = new Measure(quantity, events);
            result.merge(input.metric(), added, (a, b) -> new Measure(a.quantity().add(b.quantity()), a.events() + b.events()));
        }
        return result;
    }

    private Audit audit(String requestId) {
        boolean usable = requestId != null && !requestId.isBlank() && requestId.length() <= 64 && requestId.chars().allMatch(c -> c > 32 && c < 127);
        return new Audit(currentActor.get().auditName(), usable ? requestId : UUID.randomUUID().toString());
    }
}
