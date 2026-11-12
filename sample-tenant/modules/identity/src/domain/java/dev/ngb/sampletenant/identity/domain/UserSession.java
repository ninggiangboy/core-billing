package dev.ngb.sampletenant.identity.domain;

import dev.ngb.core.runtime.IdGenerator;
import java.time.Instant;
import java.util.UUID;

/** An opaque access token. Only the digest is stored, so a database read cannot be replayed. */
public record UserSession(UUID id, UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {

    public static UserSession open(IdGenerator ids, UUID userId, String tokenHash, Instant now, Instant expiresAt) {
        return new UserSession(ids.newId(), userId, tokenHash, now, expiresAt);
    }

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
