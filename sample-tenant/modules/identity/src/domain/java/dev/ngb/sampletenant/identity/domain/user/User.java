package dev.ngb.sampletenant.identity.domain.user;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** A local account. Credentials are this system's own; Billing never sees them. */
public record User(
        UUID id,
        String email,
        String displayName,
        String passwordHash,
        Role role,
        UserStatus status,
        Instant createdAt) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public User {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new BadRequestException("a valid email address is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new BadRequestException("a display name is required");
        }
    }

    public static User register(IdGenerator ids, String email, String displayName, String passwordHash, Role role, Instant now) {
        return new User(ids.newId(), email.trim().toLowerCase(Locale.ROOT), displayName.trim(), passwordHash, role, UserStatus.ACTIVE, now);
    }

    public User withRole(Role next) {
        return new User(id, email, displayName, passwordHash, next, status, createdAt);
    }

    public User disabled() {
        return new User(id, email, displayName, passwordHash, role, UserStatus.DISABLED, createdAt);
    }

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }
}
