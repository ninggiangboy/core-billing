package io.corebilling.payment.application.shared.command;

import java.util.Objects;

/** Who is acting and under which request id, for the audit trail. */
public record Audit(String actor, String requestId) {

    public Audit {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requestId, "requestId");
    }
}
