package dev.ngb.tenant.webhook.application.event.port;

import dev.ngb.tenant.webhook.domain.event.WebhookEvent;
import java.util.Optional;

/** Persistence boundary for received events, keyed by Billing's event id. */
public interface WebhookRepository {

    boolean saveIfAbsent(WebhookEvent event);

    Optional<WebhookEvent> findByBillingEventId(String billingEventId);

    void save(WebhookEvent event);
}
