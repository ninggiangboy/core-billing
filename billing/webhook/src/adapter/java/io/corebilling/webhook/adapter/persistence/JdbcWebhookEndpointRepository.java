package io.corebilling.webhook.adapter.persistence;

import io.corebilling.sharedkernel.TenantId;
import io.corebilling.webhook.application.WebhookEndpointRepository;
import io.corebilling.webhook.domain.EndpointStatus;
import io.corebilling.webhook.domain.WebhookEndpoint;
import io.corebilling.webhook.domain.WebhookEndpointId;
import java.sql.Array;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcWebhookEndpointRepository implements WebhookEndpointRepository {

    private static final String COLUMNS =
            "id, tenant_id, url, description, event_types, status, key_id, secret_ciphertext, created_at, updated_at, secret_rotated_at";

    private static final RowMapper<WebhookEndpoint> MAPPER = (rs, n) -> {
        Array types = rs.getArray("event_types");
        return WebhookEndpoint.restore(
                new WebhookEndpointId(Rows.uuid(rs, "id")),
                new TenantId(Rows.uuid(rs, "tenant_id")),
                rs.getString("url"),
                rs.getString("description"),
                List.of((String[]) types.getArray()),
                EndpointStatus.valueOf(rs.getString("status")),
                rs.getString("key_id"),
                rs.getString("secret_ciphertext"),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                Rows.instant(rs, "secret_rotated_at"));
    };

    private final JdbcClient jdbc;

    public JdbcWebhookEndpointRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(WebhookEndpoint e) {
        jdbc.sql("insert into webhook_endpoints (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(e.id().value(), e.tenantId().value(), e.url(), e.description(), e.eventTypes().toArray(String[]::new), e.status().name(),
                        e.keyId(), e.secretCiphertext(), Timestamp.from(e.createdAt()), Timestamp.from(e.updatedAt()),
                        e.secretRotatedAt() == null ? null : Timestamp.from(e.secretRotatedAt()))
                .update();
    }

    @Override
    public void update(WebhookEndpoint e) {
        int updated = jdbc.sql("""
                        update webhook_endpoints
                        set url = ?, description = ?, event_types = ?, status = ?, key_id = ?, secret_ciphertext = ?, updated_at = ?, secret_rotated_at = ?
                        where id = ?
                        """)
                .params(e.url(), e.description(), e.eventTypes().toArray(String[]::new), e.status().name(), e.keyId(), e.secretCiphertext(),
                        Timestamp.from(e.updatedAt()), e.secretRotatedAt() == null ? null : Timestamp.from(e.secretRotatedAt()), e.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("webhook endpoint " + e.id() + " was not updated");
        }
    }

    @Override
    public Optional<WebhookEndpoint> findById(WebhookEndpointId id) {
        return jdbc.sql("select " + COLUMNS + " from webhook_endpoints where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public List<WebhookEndpoint> list() {
        return jdbc.sql("select " + COLUMNS + " from webhook_endpoints order by created_at, id").query(MAPPER).list();
    }

    @Override
    public List<WebhookEndpoint> findSubscribed(String eventType) {
        return jdbc.sql("select " + COLUMNS + " from webhook_endpoints where status = 'ACTIVE' and (? = any(event_types) or '*' = any(event_types)) order by id")
                .param(eventType)
                .query(MAPPER)
                .list();
    }
}
