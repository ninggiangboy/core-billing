package dev.ngb.sampletenant.webhook.adapter.web;

import dev.ngb.sampletenant.webhook.application.ReceiveWebhookUseCase;
import dev.ngb.sampletenant.webhook.application.WebhookEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Billing's signed delivery endpoint. The signature is checked over the raw bytes before the body
 * is parsed, and a repeated event id answers 200 without applying the policy again.
 */
@RestController
public class WebhookController {

    public record EventBody(String id, String type, String tenantId, Instant occurredAt, Map<String, Object> data) {}

    private final JsonMapper json = JsonMapper.builder().build();
    private final ReceiveWebhookUseCase receive;

    public WebhookController(ReceiveWebhookUseCase receive) {
        this.receive = receive;
    }

    @PostMapping(value = "/webhooks/billing", consumes = "application/json")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "Billing-Event-Id", required = false) String eventId,
            @RequestHeader(value = "Billing-Event-Type", required = false) String eventType,
            @RequestHeader(value = "Billing-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Billing-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        EventBody parsed = json.readValue(body, EventBody.class);
        receive.receive(
                new ReceiveWebhookUseCase.Headers(eventId, eventType, timestamp, signature),
                body,
                new String(body, StandardCharsets.UTF_8),
                new WebhookEnvelope(parsed.id(), parsed.type(), parsed.tenantId(), parsed.occurredAt(), parsed.data()));
        return ResponseEntity.ok().build();
    }
}
