package dev.ngb.tenant.identity.application.user.query;

import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.identity.domain.user.UserStatus;
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
