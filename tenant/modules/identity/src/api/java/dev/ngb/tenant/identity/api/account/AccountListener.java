package dev.ngb.tenant.identity.api.account;

/** Lets another module react to an account lifecycle change without identity depending on it. */
public interface AccountListener {

    void registered(RegisteredAccount account);

    default void deactivated(RegisteredAccount account) {}
}
