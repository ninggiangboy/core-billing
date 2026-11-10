package io.corebilling.platform.api.idempotency;

import io.corebilling.sharedkernel.TenantId;
import java.util.Optional;

/**
 * Makes a request safe to retry. {@code begin} must run inside the same transaction as the work it
 * protects: it inserts the key first, so a concurrent duplicate waits on the unique index until the
 * first transaction commits and then sees its stored response.
 */
public interface IdempotencyStore {

    /**
     * @return empty when this is the first time the key is seen (proceed and call {@link #complete});
     *         the stored response when the same request already finished
     * @throws IdempotencyConflictException when the key was used with a different request hash
     */
    Optional<StoredResponse> begin(TenantId tenantId, String scope, String key, String requestHash);

    /** Records the response for a key that {@link #begin} accepted. */
    void complete(TenantId tenantId, String scope, String key, StoredResponse response);
}
