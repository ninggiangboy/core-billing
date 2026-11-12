package dev.ngb.billing.platform.application.messaging.service;

/**
 * The names platform acts under when no user is behind the change. A name is stored in the ledger entries and the
 * audit log, so it must not change once released.
 */
public final class PlatformActors {

    /** The actor that creates the first Platform administrator. */
    public static final String BOOTSTRAP = "system/bootstrap";

    private PlatformActors() {}
}
