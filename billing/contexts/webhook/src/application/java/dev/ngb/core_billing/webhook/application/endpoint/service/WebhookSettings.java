package dev.ngb.core_billing.webhook.application.endpoint.service;

import dev.ngb.core_billing.webhook.domain.delivery.DeliveryPolicy;
import java.util.Objects;

/**
 * Deployment settings of the webhook module. {@code allowInsecureTargets} is for development only: it allows
 * http and private addresses so a local receiver can be used.
 */
public record WebhookSettings(boolean allowInsecureTargets, int batchSize, DeliveryPolicy policy) {

    public WebhookSettings {
        Objects.requireNonNull(policy, "policy");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1");
        }
    }
}
