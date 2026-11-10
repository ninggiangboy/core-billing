package io.corebilling.platform.application.role.query;

import io.corebilling.platform.api.actor.Permission;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A role and its permissions as the admin GET handlers show it. */
public record RoleView(UUID id, String name, boolean system, Instant createdAt, Set<Permission> permissions) {}
