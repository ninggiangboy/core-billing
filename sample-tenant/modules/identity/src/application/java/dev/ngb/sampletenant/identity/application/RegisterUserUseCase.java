package dev.ngb.sampletenant.identity.application;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.identity.api.Role;
import dev.ngb.sampletenant.identity.api.account.AccountListener;
import dev.ngb.sampletenant.identity.api.account.RegisteredAccount;
import dev.ngb.sampletenant.identity.application.port.PasswordHasher;
import dev.ngb.sampletenant.identity.application.port.UserRepository;
import dev.ngb.sampletenant.identity.domain.User;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.ConflictException;

/** Registers a local account with its own credentials. */
public class RegisterUserUseCase {

    public static final int MIN_PASSWORD_LENGTH = 8;

    public record Command(String email, String displayName, String password, Role role) {}

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final IdGenerator ids;
    private final AccountListener listener;

    public RegisterUserUseCase(
            UserRepository users, PasswordHasher hasher, Clock clock, IdGenerator ids, AccountListener listener) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
        this.ids = ids;
        this.listener = listener;
    }

    public User execute(Command command) {
        if (command.email() == null || command.email().isBlank()) {
            throw new BadRequestException("an email address is required");
        }
        if (command.password() == null || command.password().length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("the password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (users.emailExists(command.email())) {
            throw new ConflictException("an account with this email already exists");
        }
        Role role = command.role() == null ? Role.VIEWER : command.role();
        User user = User.register(
                ids, command.email(), command.displayName(), hasher.hash(command.password()), role, clock.now());
        users.save(user);
        listener.registered(new RegisteredAccount(user.id(), user.displayName(), user.email()));
        return user;
    }

    public boolean anyUserExists() {
        return !users.findAll().isEmpty();
    }
}
