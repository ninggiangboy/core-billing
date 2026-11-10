package dev.ngb.core_billing.webhook.application.delivery.command;

import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.EventInbox;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDelivery;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDeliveryId;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpoint;
import java.util.List;
import dev.ngb.core_billing.webhook.application.delivery.port.EventBodyWriter;
import dev.ngb.core_billing.webhook.application.delivery.port.WebhookDeliveryRepository;
import dev.ngb.core_billing.webhook.application.endpoint.port.WebhookEndpointRepository;

/**
 * The webhook module's consumer of outbox events: for each subscribed endpoint of the event's tenant it queues
 * one delivery holding the body that will be sent. Handling the same event twice queues nothing new.
 */
public class RecordDeliveriesUseCase {

    public static final String CONSUMER = "webhook";

    private final UnitOfWork unitOfWork;
    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final EventInbox inbox;
    private final EventBodyWriter bodies;
    private final IdGenerator ids;
    private final Clock clock;

    public RecordDeliveriesUseCase(
            UnitOfWork unitOfWork, WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries, EventInbox inbox, EventBodyWriter bodies,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.inbox = inbox;
        this.bodies = bodies;
        this.ids = ids;
        this.clock = clock;
    }

    /** @return how many deliveries were queued */
    public int handle(DomainEvent event) {
        return unitOfWork.transactional(() -> {
            if (!inbox.firstTime(CONSUMER, event)) {
                return 0;
            }
            List<WebhookEndpoint> targets = endpoints.findSubscribed(event.type());
            if (targets.isEmpty()) {
                return 0;
            }
            String body = bodies.write(event);
            int queued = 0;
            for (WebhookEndpoint endpoint : targets) {
                WebhookDelivery delivery = WebhookDelivery.queue(
                        new WebhookDeliveryId(ids.newId()), event.tenantId(), endpoint.id(), event.eventId(), event.type(), body, null, clock.now());
                if (deliveries.addIfAbsent(delivery)) {
                    queued++;
                }
            }
            return queued;
        });
    }
}
