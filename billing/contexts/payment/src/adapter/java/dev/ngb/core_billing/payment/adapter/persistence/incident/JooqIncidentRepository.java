package dev.ngb.core_billing.payment.adapter.persistence.incident;

import static dev.ngb.core_billing_jooq.Tables.STRIPE_INCIDENTS;

import dev.ngb.core_billing.payment.application.incident.port.IncidentRepository;
import dev.ngb.core_billing.payment.domain.incident.IncidentKind;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.Text;
import java.time.Instant;
import java.util.UUID;
import org.jooq.DSLContext;

public class JooqIncidentRepository implements IncidentRepository {

    private final DSLContext dsl;

    public JooqIncidentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void record(UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, Instant now) {
        // Finding the same open incident again raises its count instead of adding a row.
        String clippedDetail = Text.clip(detail, 1000);
        dsl.insertInto(
                        STRIPE_INCIDENTS,
                        STRIPE_INCIDENTS.ID,
                        STRIPE_INCIDENTS.TENANT_ID,
                        STRIPE_INCIDENTS.CONFIGURATION_ID,
                        STRIPE_INCIDENTS.KIND,
                        STRIPE_INCIDENTS.SUBJECT,
                        STRIPE_INCIDENTS.DETAIL,
                        STRIPE_INCIDENTS.CREATED_AT,
                        STRIPE_INCIDENTS.LAST_SEEN_AT)
                .values(
                        id,
                        tenantId.value(),
                        configurationId == null ? null : configurationId.value(),
                        kind.name(),
                        Text.clip(subject, 300),
                        clippedDetail,
                        now,
                        now)
                .onConflict(STRIPE_INCIDENTS.TENANT_ID, STRIPE_INCIDENTS.KIND, STRIPE_INCIDENTS.SUBJECT)
                .where(STRIPE_INCIDENTS.RESOLVED_AT.isNull())
                .doUpdate()
                .set(STRIPE_INCIDENTS.OCCURRENCES, STRIPE_INCIDENTS.OCCURRENCES.plus(1))
                .set(STRIPE_INCIDENTS.LAST_SEEN_AT, now)
                .set(STRIPE_INCIDENTS.DETAIL, clippedDetail)
                .execute();
    }

    @Override
    public boolean resolve(UUID id, Instant now) {
        return dsl.update(STRIPE_INCIDENTS)
                .set(STRIPE_INCIDENTS.RESOLVED_AT, now)
                .where(STRIPE_INCIDENTS.ID.eq(id))
                .and(STRIPE_INCIDENTS.RESOLVED_AT.isNull())
                .execute() == 1;
    }
}
