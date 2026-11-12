package dev.ngb.tenant.identity.domain.user;

/** Whether an account can authenticate. A disabled account keeps its history. */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
