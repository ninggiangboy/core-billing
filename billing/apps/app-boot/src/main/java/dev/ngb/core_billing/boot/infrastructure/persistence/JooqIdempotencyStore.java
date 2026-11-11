package dev.ngb.core_billing.boot.infrastructure.persistence;

import static dev.ngb.core_billing_jooq.Tables.IDEMPOTENCY_KEYS;

import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.platform.api.idempotency.IdempotencyStore;
import dev.ngb.core_billing.platform.api.idempotency.StoredResponse;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing_jooq.tables.records.IdempotencyKeysRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class JooqIdempotencyStore implements IdempotencyStore {

    private final DSLContext dsl;

    public JooqIdempotencyStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<StoredResponse> begin(TenantId tenantId, String scope, String key, String requestHash) {
        requireTransaction();
        int inserted = dsl.insertInto(IDEMPOTENCY_KEYS)
                .set(IDEMPOTENCY_KEYS.TENANT_ID, tenantId.value())
                .set(IDEMPOTENCY_KEYS.SCOPE, scope)
                .set(IDEMPOTENCY_KEYS.KEY, key)
                .set(IDEMPOTENCY_KEYS.REQUEST_HASH, requestHash)
                .onConflict(IDEMPOTENCY_KEYS.TENANT_ID, IDEMPOTENCY_KEYS.SCOPE, IDEMPOTENCY_KEYS.KEY)
                .doNothing()
                .execute();
        if (inserted == 1) {
            return Optional.empty();
        }
        IdempotencyKeysRecord existing = dsl.selectFrom(IDEMPOTENCY_KEYS)
                .where(IDEMPOTENCY_KEYS.TENANT_ID.eq(tenantId.value()))
                .and(IDEMPOTENCY_KEYS.SCOPE.eq(scope))
                .and(IDEMPOTENCY_KEYS.KEY.eq(key))
                .fetchOne();
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(scope, key);
        }
        if (existing.getResponseStatus() == null) {
            throw new IllegalStateException("idempotency key was accepted but never completed: %s/%s".formatted(scope, key));
        }
        return Optional.of(new StoredResponse(existing.getResponseStatus(), existing.getResponseBody()));
    }

    @Override
    public void complete(TenantId tenantId, String scope, String key, StoredResponse response) {
        requireTransaction();
        int updated = dsl.update(IDEMPOTENCY_KEYS)
                .set(IDEMPOTENCY_KEYS.RESPONSE_STATUS, response.status())
                .set(IDEMPOTENCY_KEYS.RESPONSE_BODY, response.body())
                .where(IDEMPOTENCY_KEYS.TENANT_ID.eq(tenantId.value()))
                .and(IDEMPOTENCY_KEYS.SCOPE.eq(scope))
                .and(IDEMPOTENCY_KEYS.KEY.eq(key))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("idempotency key was not begun: %s/%s".formatted(scope, key));
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("idempotency must run inside the unit of work it protects");
        }
    }
}
