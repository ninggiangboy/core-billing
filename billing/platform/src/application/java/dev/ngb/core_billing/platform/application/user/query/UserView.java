package dev.ngb.core_billing.platform.application.user.query;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.domain.user.UserStatus;
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
