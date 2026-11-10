package io.corebilling.boot.infrastructure;

import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.TenantId;
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
                        insert into audit_log (id, scope, tenant_id, actor, action, subject, request_id, occurred_at)
                        values (?, ?, cast(? as uuid), ?, ?, ?, ?, ?)
                        """)
                .param(ids.newId())
                .param(entry.tenant().isPresent() ? "TENANT" : "PLATFORM")
                .param(entry.tenant().map(TenantId::toString).orElse(null))
                .param(entry.actor())
                .param(entry.action())
                .param(entry.target())
                .param(entry.requestId())
                .param(Timestamp.from(entry.occurredAt()))
                .update();
    }
}
