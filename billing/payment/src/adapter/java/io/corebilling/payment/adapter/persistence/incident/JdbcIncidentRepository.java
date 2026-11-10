package io.corebilling.payment.adapter.persistence.incident;

import io.corebilling.payment.adapter.persistence.Rows;

import io.corebilling.payment.application.incident.port.IncidentRepository;
import io.corebilling.payment.domain.incident.IncidentKind;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcIncidentRepository implements IncidentRepository {

    private final JdbcClient jdbc;

    public JdbcIncidentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, Instant now) {
        // Finding the same open incident again raises its count instead of adding a row.
        jdbc.sql("""
                        insert into stripe_incidents (id, tenant_id, configuration_id, kind, subject, detail, created_at, last_seen_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (tenant_id, kind, subject) where resolved_at is null
                        do update set occurrences = stripe_incidents.occurrences + 1, last_seen_at = excluded.last_seen_at, detail = excluded.detail
                        """)
                .params(id, tenantId.value(), configurationId == null ? null : configurationId.value(), kind.name(), clip(subject, 300), clip(detail, 1000),
                        Rows.timestamp(now), Rows.timestamp(now))
                .update();
    }

    @Override
    public boolean resolve(UUID id, Instant now) {
        return jdbc.sql("update stripe_incidents set resolved_at = ? where id = ? and resolved_at is null").params(Rows.timestamp(now), id).update() == 1;
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
