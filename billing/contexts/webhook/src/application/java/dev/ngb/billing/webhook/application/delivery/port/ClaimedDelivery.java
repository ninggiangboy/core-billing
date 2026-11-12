package dev.ngb.billing.webhook.application.delivery.port;

import dev.ngb.billing.webhook.domain.delivery.WebhookDelivery;

/** A delivery a worker now holds, with what it needs to send it. {@code delivery.attempts()} already counts this attempt. */
public record ClaimedDelivery(WebhookDelivery delivery, String url, String keyId, String secretCiphertext) {}
