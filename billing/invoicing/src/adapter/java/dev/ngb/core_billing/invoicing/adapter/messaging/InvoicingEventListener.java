package dev.ngb.core_billing.invoicing.adapter.messaging;

import dev.ngb.core_billing.invoicing.application.cycle.command.CollectChargeUseCase;
import dev.ngb.core_billing.sharedkernel.EventEnvelope;
import org.springframework.context.event.EventListener;

/**
 * Receives each outbox event inside the relay's transaction. An exception makes the relay retry the event later,
 * which is safe because every consumer here records the event in the inbox in the same transaction as its effect.
 */
public class InvoicingEventListener {

    private final CollectChargeUseCase collectCharge;

    public InvoicingEventListener(CollectChargeUseCase collectCharge) {
        this.collectCharge = collectCharge;
    }

    @EventListener
    public void on(EventEnvelope event) {
        collectCharge.handle(event);
    }
}
