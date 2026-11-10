package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.IncidentRepository;
import io.corebilling.payment.domain.Incident;
import io.corebilling.payment.domain.IncidentKind;
import io.corebilling.payment.domain.StripeConfigurationId;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcIncidentRepository implements IncidentRepository {

    private final JdbcClient jdbc;

    public JdbcIncidentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, Instant now) {
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
    public List<Incident> list(boolean openOnly, int limit) {
        return jdbc.sql("""
                        select id, tenant_id, configuration_id, kind, subject, detail, occurrences, created_at, last_seen_at, resolved_at
                        from stripe_incidents
                        where (? = false or resolved_at is null)
                        order by last_seen_at desc, id
                        limit ?
                        """)
                .params(openOnly, limit)
                .query((rs, n) -> {
                    UUID configuration = Rows.uuid(rs, "configuration_id");
                    return new Incident(Rows.uuid(rs, "id"), new TenantId(Rows.uuid(rs, "tenant_id")),
                            configuration == null ? null : new StripeConfigurationId(configuration), IncidentKind.valueOf(rs.getString("kind")),
                            rs.getString("subject"), rs.getString("detail"), rs.getInt("occurrences"), Rows.instant(rs, "created_at"),
                            Rows.instant(rs, "last_seen_at"), Rows.instant(rs, "resolved_at"));
                })
                .list();
    }

    @Override
    public boolean resolve(UUID id, Instant now) {
        return jdbc.sql("update stripe_incidents set resolved_at = ? where id = ? and resolved_at is null").params(Rows.timestamp(now), id).update() == 1;
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
