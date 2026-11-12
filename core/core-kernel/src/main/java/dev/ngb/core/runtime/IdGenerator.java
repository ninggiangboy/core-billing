package dev.ngb.core.runtime;

import java.util.UUID;

/** Port for creating identifiers, so domain code stays deterministic under test. */
public interface IdGenerator {

    UUID newId();
}
