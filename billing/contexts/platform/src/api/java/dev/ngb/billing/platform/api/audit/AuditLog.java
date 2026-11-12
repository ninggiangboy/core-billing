package dev.ngb.billing.platform.api.audit;

/** Records administrative actions in the caller's transaction. */
public interface AuditLog {

    void record(AuditEntry entry);
}
