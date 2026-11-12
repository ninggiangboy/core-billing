package dev.ngb.billing.platform.api.audit;

import dev.ngb.billing.platform.api.actor.Actor;
import java.util.Objects;
import java.util.UUID;

/** Who is acting and under which request id, recorded in the audit entries a use case writes. */
public record Audit(String actor, String requestId) {

    /** Longest request id kept from a client; a longer one is replaced by a fresh id. */
    public static final int MAX_REQUEST_ID = 64;

    public Audit {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requestId, "requestId");
    }

    /** The actor's audit name under the supplied request id, or a fresh id when it is unusable. */
    public static Audit of(Actor actor, String suppliedRequestId) {
        boolean usable = suppliedRequestId != null && !suppliedRequestId.isBlank()
                && suppliedRequestId.length() <= MAX_REQUEST_ID
                && suppliedRequestId.chars().allMatch(c -> c > 32 && c < 127);
        return new Audit(actor.auditName(), usable ? suppliedRequestId : UUID.randomUUID().toString());
    }
}
