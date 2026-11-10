package io.corebilling.platform.application.user.query;

import io.corebilling.platform.api.actor.Permission;
import io.corebilling.platform.domain.user.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A Billing user as the admin GET handlers show it. */
public record UserView(
        UUID id,
        String email,
        String displayName,
        String keycloakSubject,
        UserStatus status,
        Instant createdAt,
        Set<UUID> roleIds,
        Set<Permission> directPermissions) {}
