package dev.ngb.core_billing.platform.api.idempotency;

import java.util.Objects;

/** The response recorded for an idempotency key, replayed verbatim on a retry. */
public record StoredResponse(int status, String body) {
    public StoredResponse {
        Objects.requireNonNull(body, "body");
    }
}
