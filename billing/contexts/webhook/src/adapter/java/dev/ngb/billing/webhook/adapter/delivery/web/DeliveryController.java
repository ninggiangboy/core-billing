package dev.ngb.billing.webhook.adapter.delivery.web;

import dev.ngb.billing.platform.api.actor.CurrentActor;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.api.actor.RequiresPermission;
import dev.ngb.billing.platform.api.audit.Audit;
import dev.ngb.billing.webhook.application.delivery.command.ManageDeliveryUseCase;
import dev.ngb.billing.webhook.application.delivery.query.DeliveryAttemptView;
import dev.ngb.billing.webhook.application.delivery.query.DeliveryQueries;
import dev.ngb.billing.webhook.application.delivery.query.WebhookDeliveryView;
import dev.ngb.billing.webhook.domain.delivery.DeliveryStatus;
import dev.ngb.billing.webhook.domain.delivery.WebhookDelivery;
import dev.ngb.billing.webhook.domain.delivery.WebhookDeliveryId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Webhook delivery history and resending. */
@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class DeliveryController {

    public record DeliveryResponse(
            String id, String endpointId, String eventId, String eventType, String status, int attempts, Instant nextAttemptAt, String resendOf,
            Integer lastStatusCode, String lastError, Instant createdAt, Instant completedAt) {
        static DeliveryResponse of(WebhookDelivery d) {
            return new DeliveryResponse(d.id().toString(), d.endpointId().toString(), d.eventId().toString(), d.eventType(), d.status().name(),
                    d.attempts(), d.nextAttemptAt(), d.resendOf() == null ? null : d.resendOf().toString(), d.lastStatusCode(), d.lastError(),
                    d.createdAt(), d.completedAt());
        }

        static DeliveryResponse ofView(WebhookDeliveryView v) {
            return new DeliveryResponse(v.id().toString(), v.endpointId().toString(), v.eventId().toString(), v.eventType(), v.status().name(),
                    v.attempts(), v.nextAttemptAt(), v.resendOf() == null ? null : v.resendOf().toString(), v.lastStatusCode(), v.lastError(),
                    v.createdAt(), v.completedAt());
        }
    }

    public record AttemptResponse(int attemptNo, Integer statusCode, String error, String responseExcerpt, long latencyMs, Instant attemptedAt) {
        static AttemptResponse ofView(DeliveryAttemptView a) {
            return new AttemptResponse(a.attemptNo(), a.statusCode(), a.error(), a.responseExcerpt(), a.latencyMs(), a.attemptedAt());
        }
    }

    public record DeliveryDetailResponse(DeliveryResponse delivery, String body, List<AttemptResponse> attempts) {}

    private final ManageDeliveryUseCase deliveries;
    private final DeliveryQueries deliveryQueries;
    private final CurrentActor currentActor;

    public DeliveryController(ManageDeliveryUseCase deliveries, DeliveryQueries deliveryQueries, CurrentActor currentActor) {
        this.deliveries = deliveries;
        this.deliveryQueries = deliveryQueries;
        this.currentActor = currentActor;
    }

    /** Newest first. Pass the last {@code id} of a page as {@code before} for the next one. */
    @GetMapping("/webhook-deliveries")
    @RequiresPermission(Permission.WEBHOOK_READ)
    public List<DeliveryResponse> listDeliveries(
            @RequestParam(required = false) String status, @RequestParam(required = false) String endpointId,
            @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return deliveryQueries.list(
                        status == null ? null : DeliveryStatus.valueOf(status),
                        endpointId == null ? null : UUID.fromString(endpointId),
                        before == null ? null : UUID.fromString(before),
                        limit,
                        currentActor.caller())
                .stream().map(DeliveryResponse::ofView).toList();
    }

    @GetMapping("/webhook-deliveries/{id}")
    @RequiresPermission(Permission.WEBHOOK_READ)
    public DeliveryDetailResponse getDelivery(@PathVariable String id) {
        DeliveryQueries.Detail detail = deliveryQueries.get(UUID.fromString(id), currentActor.caller());
        return new DeliveryDetailResponse(DeliveryResponse.ofView(detail.delivery()), detail.delivery().body(),
                detail.attempts().stream().map(AttemptResponse::ofView).toList());
    }

    /** Queues a fresh delivery of the same stored event to the same endpoint. Answers 202: sending happens asynchronously. */
    @PostMapping("/webhook-deliveries/{id}/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresPermission(Permission.WEBHOOK_MANAGE)
    public DeliveryResponse resend(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return DeliveryResponse.of(deliveries.resend(Audit.of(currentActor.get(), requestId), WebhookDeliveryId.of(id), currentActor.caller()));
    }
}
