package io.corebilling.webhook.adapter.persistence.delivery;

import io.corebilling.webhook.application.delivery.query.DeliveryAttemptView;
import io.corebilling.webhook.application.delivery.query.DeliveryReadRepository;
import io.corebilling.webhook.application.delivery.query.WebhookDeliveryView;
import io.corebilling.webhook.domain.delivery.DeliveryStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects deliveries and attempts straight into the views; the domain aggregate is never built. */
public class JdbcWebhookDeliveryReadRepository implements DeliveryReadRepository {

    private static final String COLUMNS = "d.id, d.endpoint_id, d.event_id, d.event_type, d.body, d.status, d.attempts, d.next_attempt_at, "
            + "d.resend_of, d.last_status_code, d.last_error, d.created_at, d.completed_at";

    private final JdbcClient jdbc;

    public JdbcWebhookDeliveryReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WebhookDeliveryView> list(DeliveryStatus status, UUID endpointId, UUID before, int limit) {
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from webhook_deliveries d where true");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            sql.append(" and d.status = ?");
            params.add(status.name());
        }
        if (endpointId != null) {
            sql.append(" and d.endpoint_id = ?");
            params.add(endpointId);
        }
        if (before != null) {
            sql.append(" and d.id < ?");
            params.add(before);
        }
        sql.append(" order by d.id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(WebhookDeliveryView.class).list();
    }

    @Override
    public Optional<WebhookDeliveryView> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from webhook_deliveries d where d.id = ?").param(id).query(WebhookDeliveryView.class).optional();
    }

    @Override
    public List<DeliveryAttemptView> attempts(UUID deliveryId) {
        return jdbc.sql("select attempt_no, status_code, error, response_excerpt, latency_ms, attempted_at from webhook_delivery_attempts "
                        + "where delivery_id = ? order by attempt_no")
                .param(deliveryId)
                .query(DeliveryAttemptView.class)
                .list();
    }
}
