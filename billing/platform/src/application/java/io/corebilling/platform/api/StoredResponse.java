package io.corebilling.platform.api;

import java.util.Objects;

/** The response recorded for an idempotency key, replayed verbatim on a retry. */
public record StoredResponse(int status, String body) {
    public StoredResponse {
        Objects.requireNonNull(body, "body");
    }
}
