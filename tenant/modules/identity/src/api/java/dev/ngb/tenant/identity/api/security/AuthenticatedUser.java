package dev.ngb.tenant.identity.api.security;

import java.util.UUID;

/** The authenticated caller, put in the security context by the session filter. */
public record AuthenticatedUser(UUID id, String email, String displayName, Role role) {

    public boolean permits(Permission permission) {
        return role.permits(permission);
    }
}
