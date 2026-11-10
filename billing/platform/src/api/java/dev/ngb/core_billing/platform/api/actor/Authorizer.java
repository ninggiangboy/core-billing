package dev.ngb.core_billing.platform.api.actor;

/** Answers "may the current actor do this here". Every endpoint and use case that needs a right asks it. */
public interface Authorizer {

    /** @throws AccessDeniedException when the current actor lacks the permission in the current scope */
    void require(Permission permission);
}
