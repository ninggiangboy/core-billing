package io.corebilling.boot.infrastructure;

import io.corebilling.platform.api.audit.AuditEntry;
import io.corebilling.platform.api.audit.AuditLog;
import io.corebilling.sharedkernel.IdGenerator;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class JdbcAuditLog implements AuditLog {

    private final JdbcClient jdbc;
    private final IdGenerator ids;

    public JdbcAuditLog(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void record(AuditEntry entry) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("an audit entry must be recorded inside a unit of work");
        }
        jdbc.sql("""
                        insert into audit_log (id, tenant_id, actor, action, subject, request_id, occurred_at)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .param(ids.newId())
                .param(entry.tenantId().value())
                .param(entry.actor())
                .param(entry.action())
                .param(entry.target())
                .param(entry.requestId())
                .param(Timestamp.from(entry.occurredAt()))
                .update();
    }
}
