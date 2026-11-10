package io.corebilling.webhook.adapter.persistence.endpoint;

import io.corebilling.webhook.application.endpoint.query.EndpointReadRepository;
import io.corebilling.webhook.application.endpoint.query.WebhookEndpointView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects endpoints straight into the view; the domain aggregate is never built. */
public class JdbcWebhookEndpointReadRepository implements EndpointReadRepository {

    private static final String COLUMNS = "id, url, description, array_to_string(event_types, ',') as event_types_csv, status, key_id, "
            + "secret_ciphertext, created_at, updated_at, secret_rotated_at";

    private final JdbcClient jdbc;

    public JdbcWebhookEndpointReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WebhookEndpointView> list() {
        return jdbc.sql("select " + COLUMNS + " from webhook_endpoints order by created_at, id").query(WebhookEndpointView.class).list();
    }

    @Override
    public Optional<WebhookEndpointView> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from webhook_endpoints where id = ?").param(id).query(WebhookEndpointView.class).optional();
    }
}
