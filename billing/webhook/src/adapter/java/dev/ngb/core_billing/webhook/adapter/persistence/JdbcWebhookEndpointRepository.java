package dev.ngb.core_billing.webhook.adapter.persistence;

import dev.ngb.core_billing.webhook.application.endpoint.port.WebhookEndpointRepository;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpointId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcWebhookEndpointRepository implements WebhookEndpointRepository {

    private static final String INSERT_COLUMNS =
            "id, tenant_id, tenant_system_id, url, description, event_types, status, key_id, secret_ciphertext, created_at, updated_at, secret_rotated_at";
    private static final String SELECT_COLUMNS = "id, tenant_id, tenant_system_id, url, description, array_to_string(event_types, ',') as event_types_csv, "
            + "status, key_id, secret_ciphertext, created_at, updated_at, secret_rotated_at";

    private final JdbcClient jdbc;

    public JdbcWebhookEndpointRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(WebhookEndpoint e) {
        jdbc.sql("""
                        insert into webhook_endpoints (%s)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(INSERT_COLUMNS))
                .params(e.id().value(), e.tenantId().value(), e.tenantSystemId() == null ? null : e.tenantSystemId().value(), e.url(), e.description(),
                        e.eventTypes().toArray(String[]::new), e.status().name(), e.keyId(), e.secretCiphertext(), Timestamp.from(e.createdAt()),
                        Timestamp.from(e.updatedAt()), e.secretRotatedAt() == null ? null : Timestamp.from(e.secretRotatedAt()))
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
        return jdbc.sql("""
                        select %s
                        from webhook_endpoints
                        where id = ?
                        """.formatted(SELECT_COLUMNS))
                .param(id.value())
                .query(WebhookEndpointRow.class)
                .optional()
                .map(WebhookEndpointRow::toDomain);
    }

    @Override
    public List<WebhookEndpoint> findSubscribed(String eventType) {
        return jdbc.sql("""
                        select %s
                        from webhook_endpoints
                        where status = 'ACTIVE' and (? = any(event_types) or '*' = any(event_types))
                        order by id
                        """.formatted(SELECT_COLUMNS))
                .param(eventType)
                .query(WebhookEndpointRow.class)
                .list()
                .stream()
                .map(WebhookEndpointRow::toDomain)
                .toList();
    }
}
