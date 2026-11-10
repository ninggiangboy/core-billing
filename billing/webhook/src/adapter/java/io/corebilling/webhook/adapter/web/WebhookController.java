package io.corebilling.webhook.adapter.web;

import io.corebilling.platform.api.CurrentActor;
import io.corebilling.platform.api.Permission;
import io.corebilling.platform.api.RequiresPermission;
import io.corebilling.webhook.application.Audit;
import io.corebilling.webhook.application.CreateEndpointUseCase;
import io.corebilling.webhook.application.DeliveryAdministration;
import io.corebilling.webhook.application.EndpointAdministration;
import io.corebilling.webhook.domain.DeliveryAttempt;
import io.corebilling.webhook.domain.DeliveryStatus;
import io.corebilling.webhook.domain.EndpointStatus;
import io.corebilling.webhook.domain.WebhookDelivery;
import io.corebilling.webhook.domain.WebhookDeliveryId;
import io.corebilling.webhook.domain.WebhookEndpoint;
import io.corebilling.webhook.domain.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook endpoints, their delivery history and resending. The signing secret appears in exactly two
 * responses, creating an endpoint and rotating its secret, and never again.
 */
@RestController
@RequestMapping("/api/v1")
public class WebhookController {

    public record CreateEndpointRequest(String url, String description, List<String> eventTypes) {}

    public record UpdateEndpointRequest(String url, String description, List<String> eventTypes, String status) {}

    public record EndpointResponse(
            String id, String url, String description, List<String> eventTypes, String status, String keyId, Instant createdAt, Instant updatedAt,
            Instant secretRotatedAt) {
        static EndpointResponse of(WebhookEndpoint e) {
            return new EndpointResponse(e.id().toString(), e.url(), e.description(), List.copyOf(e.eventTypes()), e.status().name(), e.keyId(),
                    e.createdAt(), e.updatedAt(), e.secretRotatedAt());
        }
    }

    /** The one response that carries the plaintext secret. */
    public record EndpointWithSecretResponse(EndpointResponse endpoint, String signingSecret) {}

    public record DeliveryResponse(
            String id, String endpointId, String eventId, String eventType, String status, int attempts, Instant nextAttemptAt, String resendOf,
            Integer lastStatusCode, String lastError, Instant createdAt, Instant completedAt) {
        static DeliveryResponse of(WebhookDelivery d) {
            return new DeliveryResponse(d.id().toString(), d.endpointId().toString(), d.eventId().toString(), d.eventType(), d.status().name(),
                    d.attempts(), d.nextAttemptAt(), d.resendOf() == null ? null : d.resendOf().toString(), d.lastStatusCode(), d.lastError(),
                    d.createdAt(), d.completedAt());
        }
    }

    public record AttemptResponse(int attemptNo, Integer statusCode, String error, String responseExcerpt, long latencyMs, Instant attemptedAt) {
        static AttemptResponse of(DeliveryAttempt a) {
            return new AttemptResponse(a.attemptNo(), a.statusCode(), a.error(), a.responseExcerpt(), a.latencyMs(), a.attemptedAt());
        }
    }

    public record DeliveryDetailResponse(DeliveryResponse delivery, String body, List<AttemptResponse> attempts) {}

    private final CreateEndpointUseCase create;
    private final EndpointAdministration endpoints;
    private final DeliveryAdministration deliveries;
    private final CurrentActor currentActor;

    public WebhookController(
            CreateEndpointUseCase create, EndpointAdministration endpoints, DeliveryAdministration deliveries, CurrentActor currentActor) {
        this.create = create;
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.currentActor = currentActor;
    }

    @PostMapping("/webhook-endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.WEBHOOK_MANAGE)
    public EndpointWithSecretResponse createEndpoint(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody CreateEndpointRequest request) {
        CreateEndpointUseCase.Result result = create.execute(
                new CreateEndpointUseCase.Command(request.url(), request.description(), request.eventTypes(), audit(requestId)));
        return new EndpointWithSecretResponse(EndpointResponse.of(result.endpoint()), result.signingSecret());
    }

    @GetMapping("/webhook-endpoints")
    @RequiresPermission(Permission.WEBHOOK_READ)
    public List<EndpointResponse> listEndpoints() {
        return endpoints.list().stream().map(EndpointResponse::of).toList();
    }

    @GetMapping("/webhook-endpoints/{id}")
    @RequiresPermission(Permission.WEBHOOK_READ)
    public EndpointResponse getEndpoint(@PathVariable String id) {
        return EndpointResponse.of(endpoints.get(WebhookEndpointId.of(id)));
    }

    /** Changes only the fields that are present. Setting {@code status} to {@code DISABLED} pauses delivery without losing queued events. */
    @PatchMapping("/webhook-endpoints/{id}")
    @RequiresPermission(Permission.WEBHOOK_MANAGE)
    public EndpointResponse updateEndpoint(
            @PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody UpdateEndpointRequest request) {
        EndpointStatus status = request.status() == null ? null : EndpointStatus.valueOf(request.status());
        return EndpointResponse.of(endpoints.update(audit(requestId), WebhookEndpointId.of(id),
                new EndpointAdministration.Change(request.url(), request.description(), request.eventTypes(), status)));
    }

    @PostMapping("/webhook-endpoints/{id}/rotate-secret")
    @RequiresPermission(Permission.WEBHOOK_MANAGE)
    public EndpointWithSecretResponse rotateSecret(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        EndpointAdministration.Rotated rotated = endpoints.rotateSecret(audit(requestId), WebhookEndpointId.of(id));
        return new EndpointWithSecretResponse(EndpointResponse.of(rotated.endpoint()), rotated.signingSecret());
    }

    /** Newest first. Pass the last {@code id} of a page as {@code before} for the next one. */
    @GetMapping("/webhook-deliveries")
    @RequiresPermission(Permission.WEBHOOK_READ)
    public List<DeliveryResponse> listDeliveries(
            @RequestParam(required = false) String status, @RequestParam(required = false) String endpointId,
            @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return deliveries.list(
                        status == null ? null : DeliveryStatus.valueOf(status),
                        endpointId == null ? null : WebhookEndpointId.of(endpointId),
                        before == null ? null : WebhookDeliveryId.of(before),
                        limit)
                .stream().map(DeliveryResponse::of).toList();
    }

    @GetMapping("/webhook-deliveries/{id}")
    @RequiresPermission(Permission.WEBHOOK_READ)
    public DeliveryDetailResponse getDelivery(@PathVariable String id) {
        DeliveryAdministration.Detail detail = deliveries.get(WebhookDeliveryId.of(id));
        return new DeliveryDetailResponse(DeliveryResponse.of(detail.delivery()), detail.delivery().body(),
                detail.attempts().stream().map(AttemptResponse::of).toList());
    }

    /** Queues a fresh delivery of the same stored event to the same endpoint. Answers 202: sending happens asynchronously. */
    @PostMapping("/webhook-deliveries/{id}/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresPermission(Permission.WEBHOOK_MANAGE)
    public DeliveryResponse resend(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return DeliveryResponse.of(deliveries.resend(audit(requestId), WebhookDeliveryId.of(id)));
    }

    private Audit audit(String requestId) {
        boolean usable = requestId != null && !requestId.isBlank() && requestId.length() <= 64 && requestId.chars().allMatch(c -> c > 32 && c < 127);
        return new Audit(currentActor.get().auditName(), usable ? requestId : UUID.randomUUID().toString());
    }
}
