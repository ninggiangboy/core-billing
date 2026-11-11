package dev.ngb.core_billing.webhook.application.delivery.query;

import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.PageSize;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryStatus;
import java.util.List;
import java.util.UUID;

/** Reads delivery history for the admin GET handlers; the write side is not involved. */
public class DeliveryQueries {


    public record Detail(WebhookDeliveryView delivery, List<DeliveryAttemptView> attempts) {}

    private final UnitOfWork unitOfWork;
    private final DeliveryReadRepository deliveries;

    public DeliveryQueries(UnitOfWork unitOfWork, DeliveryReadRepository deliveries) {
        this.unitOfWork = unitOfWork;
        this.deliveries = deliveries;
    }

    public List<WebhookDeliveryView> list(DeliveryStatus status, UUID endpointId, UUID before, int limit) {
        return list(status, endpointId, before, limit, null);
    }

    /** Newest first. {@code status}, {@code endpointId} and {@code before} (the last id of the previous page) are optional filters. */
    public List<WebhookDeliveryView> list(DeliveryStatus status, UUID endpointId, UUID before, int limit, UUID caller) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        return unitOfWork.transactional(() -> deliveries.list(status, endpointId, before, PageSize.of(limit), caller));
    }

    public Detail get(UUID id) {
        return get(id, null);
    }

    public Detail get(UUID id, UUID caller) {
        return unitOfWork.transactional(() -> deliveries.findById(id, caller)
                .map(delivery -> new Detail(delivery, deliveries.attempts(id)))
                .orElseThrow(() -> new NotFoundException("webhook delivery not found")));
    }
}
