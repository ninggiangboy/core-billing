package dev.ngb.core_billing.webhook.adapter.endpoint.web;

import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.webhook.application.endpoint.command.CreateEndpointUseCase;
import dev.ngb.core_billing.webhook.application.endpoint.command.ManageEndpointUseCase;
import dev.ngb.core_billing.webhook.application.endpoint.query.EndpointQueries;
import dev.ngb.core_billing.webhook.application.endpoint.query.WebhookEndpointView;
import dev.ngb.core_billing.webhook.domain.endpoint.EndpointStatus;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Webhook endpoints. The signing secret appears in exactly two responses, creating an endpoint and rotating its secret, and never again. */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class EndpointController {

    public record CreateEndpointRequest(String url, String description, List<String> eventTypes) {}

    public record UpdateEndpointRequest(String url, String description, List<String> eventTypes, String status) {}

    public record EndpointResponse(
            String id, String url, String description, List<String> eventTypes, String status, String keyId, Instant createdAt, Instant updatedAt,
            Instant secretRotatedAt) {
        static EndpointResponse of(WebhookEndpoint e) {
            return new EndpointResponse(e.id().toString(), e.url(), e.description(), List.copyOf(e.eventTypes()), e.status().name(), e.keyId(),
                    e.createdAt(), e.updatedAt(), e.secretRotatedAt());
        }

        static EndpointResponse ofView(WebhookEndpointView v) {
            return new EndpointResponse(v.id().toString(), v.url(), v.description(), v.eventTypes(), v.status().name(), v.keyId(),
                    v.createdAt(), v.updatedAt(), v.secretRotatedAt());
        }
    }

    /** The one response that carries the plaintext secret. */
    public record EndpointWithSecretResponse(EndpointResponse endpoint, String signingSecret) {}

    private final CreateEndpointUseCase create;
    private final ManageEndpointUseCase endpoints;
    private final EndpointQueries endpointQueries;
    private final CurrentActor currentActor;

    public EndpointController(
            CreateEndpointUseCase create, ManageEndpointUseCase endpoints, EndpointQueries endpointQueries, CurrentActor currentActor) {
        this.create = create;
        this.endpoints = endpoints;
        this.endpointQueries = endpointQueries;
        this.currentActor = currentActor;
    }

    @PostMapping("/webhook-endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.WEBHOOK_MANAGE)
    public EndpointWithSecretResponse createEndpoint(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody CreateEndpointRequest request) {
        CreateEndpointUseCase.Result result = create.execute(
                new CreateEndpointUseCase.Command(request.url(), request.description(), request.eventTypes(), Audit.of(currentActor.get(), requestId), currentActor.caller()));
        return new EndpointWithSecretResponse(EndpointResponse.of(result.endpoint()), result.signingSecret());
    }

    @GetMapping("/webhook-endpoints")
    @RequiresPermission(Permission.WEBHOOK_READ)
    public List<EndpointResponse> listEndpoints() {
        return endpointQueries.list(currentActor.caller()).stream().map(EndpointResponse::ofView).toList();
    }

    @GetMapping("/webhook-endpoints/{id}")
    @RequiresPermission(Permission.WEBHOOK_READ)
    public EndpointResponse getEndpoint(@PathVariable String id) {
        return EndpointResponse.ofView(endpointQueries.get(UUID.fromString(id), currentActor.caller()));
    }

    /** Changes only the fields that are present. Setting {@code status} to {@code DISABLED} pauses delivery without losing queued events. */
    @PatchMapping("/webhook-endpoints/{id}")
    @RequiresPermission(Permission.WEBHOOK_MANAGE)
    public EndpointResponse updateEndpoint(
            @PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody UpdateEndpointRequest request) {
        EndpointStatus status = request.status() == null ? null : EndpointStatus.valueOf(request.status());
        return EndpointResponse.of(endpoints.update(Audit.of(currentActor.get(), requestId), WebhookEndpointId.of(id),
                new ManageEndpointUseCase.Change(request.url(), request.description(), request.eventTypes(), status), currentActor.caller()));
    }

    @PostMapping("/webhook-endpoints/{id}/rotate-secret")
    @RequiresPermission(Permission.WEBHOOK_MANAGE)
    public EndpointWithSecretResponse rotateSecret(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        ManageEndpointUseCase.Rotated rotated = endpoints.rotateSecret(Audit.of(currentActor.get(), requestId), WebhookEndpointId.of(id), currentActor.caller());
        return new EndpointWithSecretResponse(EndpointResponse.of(rotated.endpoint()), rotated.signingSecret());
    }
}
