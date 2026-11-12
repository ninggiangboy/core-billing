package dev.ngb.sampletenant.identity.adapter.session.web;

import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.session.command.AuthenticateUseCase;
import dev.ngb.sampletenant.identity.application.user.command.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.application.session.service.SessionService;
import dev.ngb.sampletenant.identity.domain.user.User;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registration, login and logout with this system's own credentials. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(String email, String displayName, String password) {}

    public record LoginRequest(String email, String password) {}

    public record LoginResponse(String token, Instant expiresAt, UserView user) {}

    public record UserView(java.util.UUID id, String email, String displayName, Role role, String status, Instant createdAt) {
        static UserView of(User user) {
            return new UserView(user.id(), user.email(), user.displayName(), user.role(), user.status().name(), user.createdAt());
        }
    }

    private final RegisterUserUseCase register;
    private final AuthenticateUseCase authenticate;
    private final SessionService sessions;

    public AuthController(RegisterUserUseCase register, AuthenticateUseCase authenticate, SessionService sessions) {
        this.register = register;
        this.authenticate = authenticate;
        this.sessions = sessions;
    }

    /** The first account becomes the administrator; every later self-registration is a viewer. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@RequestBody RegisterRequest request) {
        Role role = register.anyUserExists() ? Role.VIEWER : Role.ADMIN;
        User user = register.execute(new RegisterUserUseCase.Command(
                request.email(), request.displayName(), request.password(), role));
        return UserView.of(user);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        AuthenticateUseCase.Result result = authenticate.execute(request.email(), request.password());
        return new LoginResponse(result.token(), result.expiresAt(), UserView.of(result.user()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        sessions.logout(bearer(authorization));
    }

    static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
