package dev.ngb.billing.webhook.application.delivery.query;

import dev.ngb.billing.webhook.domain.delivery.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of deliveries and their attempt log. */
public interface DeliveryReadRepository {

    /** Newest first; {@code before} is the id of the last row of the previous page. A null system sees every endpoint. */
    List<WebhookDeliveryView> list(DeliveryStatus status, UUID endpointId, UUID before, int limit, UUID tenantSystemId);

    Optional<WebhookDeliveryView> findById(UUID id, UUID tenantSystemId);

    List<DeliveryAttemptView> attempts(UUID deliveryId);
}
