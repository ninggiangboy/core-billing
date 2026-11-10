package io.corebilling.boot.infrastructure;

import io.corebilling.platform.api.IdempotencyConflictException;
import io.corebilling.platform.api.IdempotencyStore;
import io.corebilling.platform.api.StoredResponse;
import io.corebilling.sharedkernel.TenantId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    public JdbcIdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StoredResponse> begin(TenantId tenantId, String scope, String key, String requestHash) {
        requireTransaction();
        int inserted = jdbc.sql("""
                        insert into idempotency_keys (tenant_id, scope, key, request_hash)
                        values (?, ?, ?, ?)
                        on conflict do nothing
                        """)
                .params(tenantId.value(), scope, key, requestHash)
                .update();
        if (inserted == 1) {
            return Optional.empty();
        }
        record Existing(String requestHash, Integer status, String body) {}
        Existing existing = jdbc.sql("""
                        select request_hash, response_status, response_body
                        from idempotency_keys where tenant_id = ? and scope = ? and key = ?
                        """)
                .params(tenantId.value(), scope, key)
                .query((rs, row) -> new Existing(
                        rs.getString("request_hash"), (Integer) rs.getObject("response_status"), rs.getString("response_body")))
                .single();
        if (!existing.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(scope, key);
        }
        if (existing.status() == null) {
            throw new IllegalStateException("idempotency key was accepted but never completed: " + scope + "/" + key);
        }
        return Optional.of(new StoredResponse(existing.status(), existing.body()));
    }

    @Override
    public void complete(TenantId tenantId, String scope, String key, StoredResponse response) {
        requireTransaction();
        int updated = jdbc.sql("""
                        update idempotency_keys set response_status = ?, response_body = ?
                        where tenant_id = ? and scope = ? and key = ?
                        """)
                .params(response.status(), response.body(), tenantId.value(), scope, key)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("idempotency key was not begun: " + scope + "/" + key);
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("idempotency must run inside the unit of work it protects");
        }
    }
}
