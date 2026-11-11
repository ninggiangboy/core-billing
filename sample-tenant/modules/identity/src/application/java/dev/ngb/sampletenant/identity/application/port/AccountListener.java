package dev.ngb.sampletenant.identity.application.port;

import dev.ngb.sampletenant.identity.domain.User;

/** Lets another module react to an account lifecycle change without identity depending on it. */
public interface AccountListener {

    void registered(User user);

    default void deactivated(User user) {}
}
