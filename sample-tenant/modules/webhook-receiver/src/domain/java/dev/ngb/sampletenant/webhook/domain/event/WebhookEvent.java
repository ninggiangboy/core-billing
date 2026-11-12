package dev.ngb.sampletenant.webhook.domain.event;

import dev.ngb.core.ddd.AggregateRoot;
import dev.ngb.core.runtime.IdGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One received Billing event, kept so a repeat delivery is recognised and the decision is auditable. */
public final class WebhookEvent extends AggregateRoot<UUID> {

    private final String billingEventId;
    private final String type;
    private final String tenantId;
    private final Instant occurredAt;
    private final String payload;
    private final Instant receivedAt;
    private final String status;
    private final String decision;
    private final String detail;

    private WebhookEvent(
            UUID id,
            String billingEventId,
            String type,
            String tenantId,
            Instant occurredAt,
            String payload,
            Instant receivedAt,
            String status,
            String decision,
            String detail) {
        super(id);
        this.billingEventId = Objects.requireNonNull(billingEventId, "billingEventId");
        this.type = Objects.requireNonNull(type, "type");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.decision = decision;
        this.detail = detail;
    }

    public static WebhookEvent received(
            IdGenerator ids,
            String billingEventId, String type, String tenantId, Instant occurredAt, String payload, Instant receivedAt) {
        return new WebhookEvent(
                ids.newId(), billingEventId, type, tenantId, occurredAt, payload, receivedAt, "RECEIVED", null, null);
    }

    public static WebhookEvent restore(
            UUID id,
            String billingEventId,
            String type,
            String tenantId,
            Instant occurredAt,
            String payload,
            Instant receivedAt,
            String status,
            String decision,
            String detail) {
        return new WebhookEvent(id, billingEventId, type, tenantId, occurredAt, payload, receivedAt, status, decision, detail);
    }

    public WebhookEvent processed(String nextDecision) {
        return new WebhookEvent(id(), billingEventId, type, tenantId, occurredAt, payload, receivedAt, "PROCESSED", nextDecision, detail);
    }

    public WebhookEvent failed(String reason) {
        return new WebhookEvent(id(), billingEventId, type, tenantId, occurredAt, payload, receivedAt, "FAILED", decision, reason);
    }

    public String billingEventId() {
        return billingEventId;
    }

    public String type() {
        return type;
    }

    public String tenantId() {
        return tenantId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public String payload() {
        return payload;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public String status() {
        return status;
    }

    public String decision() {
        return decision;
    }

    public String detail() {
        return detail;
    }
}
