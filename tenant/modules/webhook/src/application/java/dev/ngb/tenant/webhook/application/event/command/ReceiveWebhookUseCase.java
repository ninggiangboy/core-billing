package dev.ngb.tenant.webhook.application.event.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.shared.error.UnauthorizedException;
import dev.ngb.tenant.webhook.application.event.port.WebhookRepository;
import dev.ngb.tenant.webhook.application.event.service.WebhookEnvelope;
import dev.ngb.tenant.webhook.application.event.service.WebhookPolicy;
import dev.ngb.tenant.webhook.domain.event.WebhookEvent;
import dev.ngb.tenant.webhook.domain.event.WebhookSignature;
import java.time.Duration;

/** Verifies, deduplicates and applies one delivery. A duplicate answers without repeating the policy. */
public class ReceiveWebhookUseCase {

    public record Headers(String eventId, String eventType, String timestamp, String signature) {}

    public record Outcome(boolean duplicate, WebhookPolicy.Decision decision) {}

    private final WebhookRepository events;
    private final WebhookPolicy policy;
    private final Clock clock;
    private final IdGenerator ids;
    private final String signingSecret;
    private final Duration tolerance;

    public ReceiveWebhookUseCase(
            WebhookRepository events,
            WebhookPolicy policy,
            Clock clock,
            IdGenerator ids,
            String signingSecret,
            Duration tolerance) {
        this.events = events;
        this.policy = policy;
        this.clock = clock;
        this.ids = ids;
        this.signingSecret = signingSecret;
        this.tolerance = tolerance;
    }

    public Outcome receive(Headers headers, byte[] rawBody, String payload, WebhookEnvelope envelope) {
        boolean valid = WebhookSignature.verify(
                signingSecret,
                headers.timestamp(),
                rawBody,
                headers.signature(),
                clock.now().getEpochSecond(),
                tolerance.toSeconds());
        if (!valid || !envelope.id().equals(headers.eventId())) {
            throw new UnauthorizedException("the delivery signature could not be verified");
        }
        if (headers.eventType() != null && !headers.eventType().equals(envelope.type())) {
            throw new UnauthorizedException("the event type header does not match the body");
        }
        WebhookEvent event = WebhookEvent.received(
                ids, envelope.id(), envelope.type(), envelope.tenantId(), envelope.occurredAt(), payload, clock.now());
        if (!events.saveIfAbsent(event)) {
            return new Outcome(true, WebhookPolicy.Decision.CONTINUE);
        }
        try {
            WebhookPolicy.Decision decision = policy.apply(envelope);
            events.save(event.processed(decision.name()));
            return new Outcome(false, decision);
        } catch (RuntimeException e) {
            events.save(event.failed(message(e)));
            throw e;
        }
    }

    private static String message(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
