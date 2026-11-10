package io.corebilling.webhook.domain;

import java.util.Objects;
import java.util.UUID;

public record WebhookDeliveryId(UUID value) {

    public WebhookDeliveryId {
        Objects.requireNonNull(value, "value");
    }

    public static WebhookDeliveryId of(String text) {
        return new WebhookDeliveryId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
