package io.corebilling.customer.application.command;

import java.util.Objects;

/** Who asked and under which request, recorded in the audit entries a use case writes. */
public record AuditContext(String actor, String requestId) {

    public AuditContext {
        Objects.requireNonNull(actor, "actor");
    }
}
