package dev.ngb.sampletenant.webhook.application.event.port;

import dev.ngb.sampletenant.webhook.domain.event.WebhookEvent;
import java.util.Optional;

/** Persistence boundary for received events, keyed by Billing's event id. */
public interface WebhookRepository {

    boolean saveIfAbsent(WebhookEvent event);

    Optional<WebhookEvent> findByBillingEventId(String billingEventId);

    void save(WebhookEvent event);
}
