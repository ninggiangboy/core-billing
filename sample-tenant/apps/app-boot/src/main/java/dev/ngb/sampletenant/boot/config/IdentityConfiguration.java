package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.identity.adapter.persistence.JdbcSessionRepository;
import dev.ngb.sampletenant.identity.adapter.persistence.JdbcUserRepository;
import dev.ngb.sampletenant.identity.adapter.persistence.SessionJdbcRepository;
import dev.ngb.sampletenant.identity.adapter.persistence.UserJdbcRepository;
import dev.ngb.sampletenant.identity.adapter.security.BCryptPasswordHasher;
import dev.ngb.sampletenant.identity.application.AuthenticateUseCase;
import dev.ngb.sampletenant.identity.application.ManageUsersUseCase;
import dev.ngb.sampletenant.identity.application.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.application.SessionService;
import dev.ngb.sampletenant.identity.application.port.PasswordHasher;
import dev.ngb.sampletenant.identity.application.port.SessionRepository;
import dev.ngb.sampletenant.identity.application.port.UserRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the plain-Java identity use cases and their adapters. */
@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {

    @Bean
    UserRepository userRepository(UserJdbcRepository rows) {
        return new JdbcUserRepository(rows);
    }

    @Bean
    SessionRepository sessionRepository(SessionJdbcRepository rows) {
        return new JdbcSessionRepository(rows);
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository users, PasswordHasher hasher, Clock clock) {
        return new RegisterUserUseCase(users, hasher, clock);
    }

    @Bean
    AuthenticateUseCase authenticateUseCase(
            UserRepository users,
            SessionRepository sessions,
            PasswordHasher hasher,
            Clock clock,
            @Value("${sampletenant.identity.session-ttl:PT12H}") Duration sessionTtl) {
        return new AuthenticateUseCase(users, sessions, hasher, clock, sessionTtl);
    }

    @Bean
    SessionService sessionService(UserRepository users, SessionRepository sessions, Clock clock) {
        return new SessionService(users, sessions, clock);
    }

    @Bean
    ManageUsersUseCase manageUsersUseCase(UserRepository users) {
        return new ManageUsersUseCase(users);
    }
}
