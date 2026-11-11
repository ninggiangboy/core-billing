package dev.ngb.core_billing.boot.infrastructure;

import static dev.ngb.core_billing_jooq.Tables.AUDIT_LOG;

import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import org.jooq.DSLContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class JooqAuditLog implements AuditLog {

    private final DSLContext dsl;
    private final IdGenerator ids;

    public JooqAuditLog(DSLContext dsl, IdGenerator ids) {
        this.dsl = dsl;
        this.ids = ids;
    }

    @Override
    public void record(AuditEntry entry) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("an audit entry must be recorded inside a unit of work");
        }
        dsl.insertInto(AUDIT_LOG)
                .set(AUDIT_LOG.ID, ids.newId())
                .set(AUDIT_LOG.TENANT_ID, entry.tenantId().value())
                .set(AUDIT_LOG.ACTOR, entry.actor())
                .set(AUDIT_LOG.ACTION, entry.action())
                .set(AUDIT_LOG.SUBJECT, entry.target())
                .set(AUDIT_LOG.REQUEST_ID, entry.requestId())
                .set(AUDIT_LOG.OCCURRED_AT, entry.occurredAt())
                .execute();
    }
}
