package dev.ngb.sampletenant.shared.id;

import java.util.UUID;

/** Identifier factory. The sample tenant owns its identifiers and never reuses Billing's. */
public final class Ids {

    private Ids() {}

    public static UUID newId() {
        return UUID.randomUUID();
    }

    public static UUID parse(String value) {
        return UUID.fromString(value);
    }
}
