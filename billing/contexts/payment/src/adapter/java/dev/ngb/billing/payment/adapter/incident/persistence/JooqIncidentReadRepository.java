package dev.ngb.billing.payment.adapter.incident.persistence;

import static dev.ngb.billing_jooq.Tables.STRIPE_INCIDENTS;

import dev.ngb.billing.payment.application.incident.query.IncidentReadRepository;
import dev.ngb.billing.payment.application.incident.query.IncidentView;
import dev.ngb.billing.payment.domain.incident.IncidentKind;
import dev.ngb.billing_jooq.tables.records.StripeIncidentsRecord;
import java.util.List;
import org.jooq.DSLContext;

/** Selects incidents straight into views. */
public class JooqIncidentReadRepository implements IncidentReadRepository {

    private final DSLContext dsl;

    public JooqIncidentReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<IncidentView> list(boolean openOnly, int limit) {
        return dsl.selectFrom(STRIPE_INCIDENTS)
                .where(openOnly ? STRIPE_INCIDENTS.RESOLVED_AT.isNull() : org.jooq.impl.DSL.noCondition())
                .orderBy(STRIPE_INCIDENTS.LAST_SEEN_AT.desc(), STRIPE_INCIDENTS.ID)
                .limit(limit)
                .fetch(JooqIncidentReadRepository::toView);
    }

    private static IncidentView toView(StripeIncidentsRecord row) {
        return new IncidentView(
                row.getId(),
                IncidentKind.valueOf(row.getKind()),
                row.getSubject(),
                row.getDetail(),
                row.getOccurrences(),
                row.getCreatedAt(),
                row.getLastSeenAt(),
                row.getResolvedAt());
    }
}
