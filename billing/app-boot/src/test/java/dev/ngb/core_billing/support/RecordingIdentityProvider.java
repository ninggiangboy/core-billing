package dev.ngb.core_billing.support;

import dev.ngb.core_billing.platform.application.auth.port.IdentityProviderPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Stands in for Keycloak in tests that are about Billing's own behaviour. */
public class RecordingIdentityProvider implements IdentityProviderPort {

    public final List<NewIdentity> created = new CopyOnWriteArrayList<>();
    public final List<String> deleted = new CopyOnWriteArrayList<>();
    private final List<String> subjectsToReturn = new ArrayList<>();

    /** The next createIdentity call returns this subject instead of a fresh one. */
    public synchronized void returnSubjectOnce(String subject) {
        subjectsToReturn.add(subject);
    }

    @Override
    public synchronized String createIdentity(NewIdentity identity) {
        created.add(identity);
        return subjectsToReturn.isEmpty() ? UUID.randomUUID().toString() : subjectsToReturn.removeFirst();
    }

    @Override
    public void deleteIdentity(String subject) {
        deleted.add(subject);
    }
}
