package dev.ngb.core_billing.invoicing.adapter.subscription.web;

import dev.ngb.core_billing.invoicing.application.subscription.command.CancelSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.CreateSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionHistoryEntryView;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionQueries;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionView;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.sharedkernel.MoneyDto;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Subscriptions. Money-moving POSTs that are not repeatable by data take an {@code Idempotency-Key}. A Tenant System sees and acts on the customers mapped to it only. */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class SubscriptionController {

    public record SubscriptionResponse(
            String id, String customerId, String walletId, String planId, String planCode, String cycle, String status, Instant anchor, int periodsCharged,
            Instant currentPeriodStart, Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Instant canceledAt, Instant nextRenewalAt, int renewalAttempts,
            Instant createdAt) {
        static SubscriptionResponse of(SubscriptionView s) {
            return new SubscriptionResponse(s.id().toString(), s.customerId().toString(), s.walletId().toString(), s.planId().toString(), s.planCode(),
                    s.cycle().name(), s.status().name(), s.anchor(), s.periodsCharged(), s.currentPeriodStart(), s.currentPeriodEnd(), s.cancelAtPeriodEnd(),
                    s.canceledAt(), s.nextRenewalAt(), s.renewalAttempts(), s.createdAt());
        }
    }

    public record SubscriptionEventResponse(
            String id, String type, Integer periodNumber, Instant periodStart, Instant periodEnd, MoneyDto amount, String chargeId, String detail, Instant occurredAt) {
        static SubscriptionEventResponse of(SubscriptionHistoryEntryView e) {
            return new SubscriptionEventResponse(e.id().toString(), e.type().name(), e.periodNumber(), e.periodStart(), e.periodEnd(),
                    e.amount() == null ? null : MoneyDto.of(e.amount()), e.chargeId() == null ? null : e.chargeId().toString(), e.detail(), e.occurredAt());
        }
    }

    public record SubscriptionDetailResponse(SubscriptionResponse subscription, List<SubscriptionEventResponse> history) {}

    public record SubscribeRequest(String walletId, String planId) {}

    private final SubscriptionQueries subscriptionQueries;
    private final CreateSubscriptionUseCase createSubscription;
    private final CancelSubscriptionUseCase cancelSubscription;
    private final CurrentActor currentActor;

    public SubscriptionController(
            SubscriptionQueries subscriptionQueries, CreateSubscriptionUseCase createSubscription, CancelSubscriptionUseCase cancelSubscription,
            CurrentActor currentActor) {
        this.subscriptionQueries = subscriptionQueries;
        this.createSubscription = createSubscription;
        this.cancelSubscription = cancelSubscription;
        this.currentActor = currentActor;
    }

    /**
     * Subscribes a customer's wallet to a plan and charges the first period at once. 201 for a new subscription, 200 when the
     * {@code Idempotency-Key} was used before. A first period the wallet cannot pay leaves it {@code PAST_DUE}, not rejected.
     */
    @PostMapping("/subscriptions")
    @RequiresPermission(Permission.SUBSCRIPTION_MANAGE)
    public ResponseEntity<SubscriptionDetailResponse> subscribe(
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody SubscribeRequest request) {
        if (request.walletId() == null || request.planId() == null) {
            throw new IllegalArgumentException("walletId and planId are required");
        }
        CreateSubscriptionUseCase.Result result = createSubscription.execute(new CreateSubscriptionUseCase.Command(WalletId.of(request.walletId()),
                UUID.fromString(request.planId()), key, Audit.of(currentActor.get(), requestId), currentActor.caller()));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(subscriptionDetail(result.subscription().id().value()));
    }

    @GetMapping("/subscriptions")
    @RequiresPermission(Permission.SUBSCRIPTION_READ)
    public List<SubscriptionResponse> subscriptions(
            @RequestParam(required = false) String customerId, @RequestParam(required = false) String walletId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return subscriptionQueries.list(customerId == null ? null : UUID.fromString(customerId), walletId == null ? null : UUID.fromString(walletId),
                status == null ? null : SubscriptionStatus.valueOf(status), before == null ? null : UUID.fromString(before), limit, currentActor.caller())
                .stream().map(SubscriptionResponse::of).toList();
    }

    @GetMapping("/subscriptions/{id}")
    @RequiresPermission(Permission.SUBSCRIPTION_READ)
    public SubscriptionDetailResponse subscription(@PathVariable String id) {
        return subscriptionDetail(UUID.fromString(id));
    }

    /**
     * Cancels at the end of the paid period, or at once when no paid period is running. Repeating it changes nothing.
     * Billing stops no Resource: the Tenant System decides what the end means for it.
     */
    @PostMapping("/subscriptions/{id}/cancel")
    @RequiresPermission(Permission.SUBSCRIPTION_MANAGE)
    public SubscriptionDetailResponse cancel(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Subscription canceled = cancelSubscription.execute(new CancelSubscriptionUseCase.Command(SubscriptionId.of(id), Audit.of(currentActor.get(), requestId),
                currentActor.caller()));
        return subscriptionDetail(canceled.id().value());
    }

    private SubscriptionDetailResponse subscriptionDetail(UUID id) {
        SubscriptionQueries.SubscriptionDetail detail = subscriptionQueries.get(id, currentActor.caller());
        return new SubscriptionDetailResponse(SubscriptionResponse.of(detail.subscription()), detail.history().stream().map(SubscriptionEventResponse::of).toList());
    }
}
