package dev.ngb.billing.pricing.adapter.rating.web;

import dev.ngb.billing.money.api.reservation.ReservationResult;
import dev.ngb.billing.platform.api.actor.CurrentActor;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.api.actor.RequiresPermission;
import dev.ngb.billing.platform.api.idempotency.RepeatableCreate;
import dev.ngb.billing.pricing.application.rating.command.ChargeFromUsageUseCase;
import dev.ngb.billing.pricing.application.rating.command.EstimateCostUseCase;
import dev.ngb.billing.pricing.application.rating.command.ReserveFromEstimateUseCase;
import dev.ngb.billing.pricing.application.rating.query.PriceLineView;
import dev.ngb.billing.pricing.application.rating.query.RatingQueries;
import dev.ngb.billing.pricing.application.rating.query.UsageRatingView;
import dev.ngb.billing.pricing.domain.metric.Measure;
import dev.ngb.billing.pricing.domain.plan.PricingPlanId;
import dev.ngb.billing.pricing.domain.rating.RatingStatus;
import dev.ngb.billing.pricing.domain.rating.UsageRating;
import dev.ngb.billing.pricing.domain.rule.PriceLine;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.money.MoneyDto;
import dev.ngb.billing.sharedkernel.id.WalletId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What is done with a plan: cost estimation, reserving the estimate, and charging usage. Amounts that are charged are integers in minor units. */
@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class RatingController {

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
            String planId, String planCode, int planVersion, MoneyDto amount, String exactAmount, String adjustment, List<LineResponse> lines) {
        static EstimateResponse of(EstimateCostUseCase.Estimate e) {
            return new EstimateResponse(e.plan().id().toString(), e.plan().code(), e.version().number(), MoneyDto.of(e.quote().amount()),
                    e.quote().exactAmount().stripTrailingZeros().toPlainString(), e.quote().adjustment().name(),
                    e.quote().lines().stream().map(LineResponse::of).toList());
        }
    }

    public record ReserveRequest(String walletId, String planId, Integer version, Instant at, List<UsageInput> usage, Long ttlSeconds, String reference) {}

    public record ReservationDto(String id, String status, MoneyDto amount, Instant expiresAt, boolean replayed) {
        static ReservationDto of(ReservationResult r) {
            return new ReservationDto(r.id().toString(), r.status().name(), MoneyDto.of(r.amount()), r.expiresAt(), r.replayed());
        }
    }

    public record ReserveResponse(EstimateResponse estimate, ReservationDto reservation) {}

    public record ChargeUsageRequest(String customerId, String walletId, String planId, Integer version, Instant from, Instant to) {}

    public record RatingResponse(
            String outcome, String id, String planId, Integer planVersion, MoneyDto amount, String chargeId, String failureReason, Integer usageCount,
            Instant periodStart, Instant periodEnd, List<LineResponse> lines, boolean replayed) {
        static RatingResponse of(ChargeFromUsageUseCase.Result result) {
            UsageRating r = result.rating();
            if (r == null) {
                return new RatingResponse(result.outcome().name(), null, null, null, null, null, null, 0, null, null, List.of(), false);
            }
            return new RatingResponse(result.outcome().name(), r.id().toString(), r.planId().toString(), r.planVersion(), MoneyDto.of(r.amount()),
                    r.chargeId() == null ? null : r.chargeId().toString(), r.failureReason(), r.usageCount(), r.periodStart(), r.periodEnd(),
                    r.breakdown().stream().map(LineResponse::of).toList(), result.replayed());
        }

        static RatingResponse ofView(UsageRatingView r) {
            return new RatingResponse(outcome(r.status()), r.id().toString(), r.planId().toString(), r.planVersion(), MoneyDto.of(r.amount()),
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

    private final EstimateCostUseCase estimates;
    private final ReserveFromEstimateUseCase reserve;
    private final ChargeFromUsageUseCase chargeUsage;
    private final RatingQueries ratings;
    private final CurrentActor currentActor;

    public RatingController(
            EstimateCostUseCase estimates, ReserveFromEstimateUseCase reserve, ChargeFromUsageUseCase chargeUsage, RatingQueries ratings,
            CurrentActor currentActor) {
        this.estimates = estimates;
        this.reserve = reserve;
        this.chargeUsage = chargeUsage;
        this.ratings = ratings;
        this.currentActor = currentActor;
    }

    /** A pure calculation: nothing is stored, and the same input is priced exactly as the real charge would be. */
    @PostMapping("/pricing/estimates")
    @RequiresPermission(Permission.PRICING_READ)
    public EstimateResponse estimate(@RequestBody EstimateRequest request) {
        return EstimateResponse.of(estimates.estimate(new EstimateCostUseCase.Request(PricingPlanId.of(required(request.planId(), "planId")), request.version(), request.at(), measures(request.usage()))));
    }

    /** Holds the estimated cost on the wallet. 201 for a new reservation, 200 when the key replays one, 402 when the wallet cannot cover it. */
    @PostMapping("/pricing/reservations")
    @RepeatableCreate
    @RequiresPermission(Permission.RESERVATION_MANAGE)
    public ResponseEntity<ReserveResponse> reserveEstimate(@RequestHeader("Idempotency-Key") String key, @RequestBody ReserveRequest request) {
        ReserveFromEstimateUseCase.Result result = reserve.reserve(new ReserveFromEstimateUseCase.Command(
                WalletId.of(required(request.walletId(), "walletId")), PricingPlanId.of(required(request.planId(), "planId")), request.version(), request.at(),
                measures(request.usage()),
                Duration.ofSeconds(request.ttlSeconds() == null ? 900 : request.ttlSeconds()), request.reference(), key, currentActor.caller()));
        HttpStatus status = result.reservation().replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(new ReserveResponse(EstimateResponse.of(result.estimate()), ReservationDto.of(result.reservation())));
    }

    /**
     * Prices the customer's not-yet-rated usage in {@code [from, to)} and charges the wallet. Repeating the request
     * returns the earlier result instead of charging again. 201 when charged, 200 when repeated or when there was
     * nothing to charge, 402 when the wallet could not pay (the same request succeeds after a top-up).
     */
    @PostMapping("/pricing/usage-charges")
    @RepeatableCreate(repeated = "The usage was already charged and that rating is returned, or there was nothing to charge")
    @RequiresPermission(Permission.CHARGE_CREATE)
    public ResponseEntity<RatingResponse> chargeUsage(@RequestBody ChargeUsageRequest request) {
        if (request.from() == null || request.to() == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        ChargeFromUsageUseCase.Result result = chargeUsage.execute(new ChargeFromUsageUseCase.Command(
                CustomerId.of(required(request.customerId(), "customerId")), WalletId.of(required(request.walletId(), "walletId")),
                PricingPlanId.of(required(request.planId(), "planId")), request.version(), request.from(), request.to(), currentActor.caller()));
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
        return RatingResponse.ofView(ratings.get(UUID.fromString(id), currentActor.caller()));
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
}
