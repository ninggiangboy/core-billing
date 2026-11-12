package dev.ngb.sampletenant.webhook.adapter.persistence;

import dev.ngb.sampletenant.webhook.application.port.WebhookRepository;
import dev.ngb.sampletenant.webhook.domain.WebhookEvent;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Dedupes on Billing's event id with a unique index, so a retry inserts nothing. */
public class JdbcWebhookRepository implements WebhookRepository {

    private final JdbcTemplate jdbc;

    public JdbcWebhookRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean saveIfAbsent(WebhookEvent event) {
        int inserted = jdbc.update(
                """
                insert into webhook_events
                    (id, billing_event_id, type, tenant_id, occurred_at, payload, received_at, status, decision, detail)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (billing_event_id) do nothing
                """,
                event.id(),
                event.billingEventId(),
                event.type(),
                event.tenantId(),
                Timestamp.from(event.occurredAt()),
                event.payload(),
                Timestamp.from(event.receivedAt()),
                event.status(),
                event.decision(),
                event.detail());
        return inserted > 0;
    }

    @Override
    public Optional<WebhookEvent> findByBillingEventId(String billingEventId) {
        return jdbc.query(
                        "select * from webhook_events where billing_event_id = ?",
                        (rs, row) -> WebhookEvent.restore(
                                rs.getObject("id", UUID.class),
                                rs.getString("billing_event_id"),
                                rs.getString("type"),
                                rs.getString("tenant_id"),
                                rs.getTimestamp("occurred_at").toInstant(),
                                rs.getString("payload"),
                                rs.getTimestamp("received_at").toInstant(),
                                rs.getString("status"),
                                rs.getString("decision"),
                                rs.getString("detail")),
                        billingEventId)
                .stream()
                .findFirst();
    }

    @Override
    public void save(WebhookEvent event) {
        jdbc.update(
                "update webhook_events set status = ?, decision = ?, detail = ? where id = ?",
                event.status(),
                event.decision(),
                event.detail(),
                event.id());
    }
}
