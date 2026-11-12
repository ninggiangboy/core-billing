package dev.ngb.billing.boot.infrastructure.persistence;

import static dev.ngb.billing_jooq.Tables.AUDIT_LOG;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
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
