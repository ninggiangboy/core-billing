package io.corebilling.webhook.adapter.messaging;

import io.corebilling.sharedkernel.EventEnvelope;
import io.corebilling.webhook.application.delivery.command.RecordDeliveriesUseCase;
import org.springframework.context.event.EventListener;

/**
 * Receives each outbox event inside the relay's transaction. An exception here makes the relay retry the
 * event later, which is safe because {@link RecordDeliveriesUseCase} is idempotent per event.
 */
public class WebhookEventListener {

    private final RecordDeliveriesUseCase recordDeliveries;

    public WebhookEventListener(RecordDeliveriesUseCase recordDeliveries) {
        this.recordDeliveries = recordDeliveries;
    }

    @EventListener
    public void on(EventEnvelope event) {
        recordDeliveries.handle(event);
    }
}
