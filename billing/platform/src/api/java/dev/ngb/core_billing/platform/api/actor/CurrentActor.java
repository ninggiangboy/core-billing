package dev.ngb.core_billing.platform.api.actor;

/** The authenticated {@link Actor} of the current request. */
public interface CurrentActor {

    /** @throws IllegalStateException when the request is not authenticated */
    Actor get();
}
