package io.corebilling.webhook.domain.endpoint;

import java.util.Objects;
import java.util.UUID;

public record WebhookEndpointId(UUID value) {

    public WebhookEndpointId {
        Objects.requireNonNull(value, "value");
    }

    public static WebhookEndpointId of(String text) {
        return new WebhookEndpointId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
