package io.corebilling.platform.application.auth.query;

import java.util.Objects;

/** Who asked and under which request, recorded in every audit entry the use case writes. */
public record Caller(String actor, String requestId) {

    public Caller {
        Objects.requireNonNull(actor, "actor");
    }
}
