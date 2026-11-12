package dev.ngb.tenant.webhook.application.event.service;

import java.time.Instant;
import java.util.Map;

/** The delivered document, with {@code data} left as a map because each event type has its own schema. */
public record WebhookEnvelope(String id, String type, String tenantId, Instant occurredAt, Map<String, Object> data) {}
