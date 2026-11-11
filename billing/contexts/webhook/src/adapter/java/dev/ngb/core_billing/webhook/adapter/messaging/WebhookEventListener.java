package dev.ngb.core_billing.webhook.adapter.messaging;

import dev.ngb.core_billing.sharedkernel.EventEnvelope;
import dev.ngb.core_billing.webhook.application.delivery.command.RecordDeliveriesUseCase;
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
