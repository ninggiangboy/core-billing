package dev.ngb.core_billing.payment.adapter.persistence.incident;

import dev.ngb.core_billing.payment.application.incident.query.IncidentReadRepository;
import dev.ngb.core_billing.payment.application.incident.query.IncidentView;
import dev.ngb.core_billing.payment.domain.incident.IncidentKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects incidents straight into views. */
public class JdbcIncidentReadRepository implements IncidentReadRepository {

    private record Row(
            UUID id,
            String kind,
            String subject,
            String detail,
            int occurrences,
            Instant createdAt,
            Instant lastSeenAt,
            Instant resolvedAt) {

        IncidentView toView() {
            return new IncidentView(id, IncidentKind.valueOf(kind), subject, detail, occurrences, createdAt, lastSeenAt, resolvedAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcIncidentReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<IncidentView> list(boolean openOnly, int limit) {
        return jdbc.sql("""
                        select id, kind, subject, detail, occurrences, created_at, last_seen_at, resolved_at
                        from stripe_incidents
                        where (? = false or resolved_at is null)
                        order by last_seen_at desc, id
                        limit ?
                        """)
                .params(openOnly, limit)
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }
}
