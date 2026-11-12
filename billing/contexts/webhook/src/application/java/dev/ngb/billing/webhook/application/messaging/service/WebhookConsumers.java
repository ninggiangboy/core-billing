package dev.ngb.billing.webhook.application.messaging.service;

/**
 * The names webhook consumes outbox events under. A name is part of the inbox key
 * {@code (tenant_id, consumer, event_id)}: it must never change once released, and two consumers must never share
 * one, because the second would skip every event the first has already seen.
 */
public final class WebhookConsumers {

    /** The consumer that queues a delivery for each subscribed endpoint. */
    public static final String DELIVERY = "webhook";

    private WebhookConsumers() {}
}
