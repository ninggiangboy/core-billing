package dev.ngb.tenant.identity.domain.user;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.shared.error.BadRequestException;
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
        // A missing value must reach the constructor's validation, which answers 400, not fail on trim().
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        String normalizedName = displayName == null ? null : displayName.trim();
        return new User(ids.newId(), normalizedEmail, normalizedName, passwordHash, role, UserStatus.ACTIVE, now);
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
