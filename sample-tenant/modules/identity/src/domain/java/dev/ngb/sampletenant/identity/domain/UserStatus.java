package dev.ngb.sampletenant.identity.domain;

/** Whether an account can authenticate. A disabled account keeps its history. */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
