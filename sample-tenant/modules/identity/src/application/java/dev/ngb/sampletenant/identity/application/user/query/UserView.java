package dev.ngb.sampletenant.identity.application.user.query;

import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.domain.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** A local account as the administration screens render it. */
public record UserView(
        UUID id,
        String email,
        String displayName,
        Role role,
        UserStatus status,
        Instant createdAt) {}
