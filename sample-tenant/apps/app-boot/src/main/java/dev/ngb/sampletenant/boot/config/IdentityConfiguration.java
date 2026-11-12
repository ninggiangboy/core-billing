package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.identity.adapter.session.persistence.JdbcSessionRepository;
import dev.ngb.sampletenant.identity.adapter.user.persistence.JdbcUserRepository;
import dev.ngb.sampletenant.identity.adapter.session.persistence.SessionJdbcRepository;
import dev.ngb.sampletenant.identity.adapter.user.persistence.UserJdbcRepository;
import dev.ngb.sampletenant.identity.adapter.user.security.BCryptPasswordHasher;
import dev.ngb.sampletenant.identity.application.session.command.AuthenticateUseCase;
import dev.ngb.sampletenant.identity.application.user.command.ManageUsersUseCase;
import dev.ngb.sampletenant.identity.application.user.command.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.application.session.service.SessionService;
import dev.ngb.sampletenant.identity.api.account.AccountListener;
import dev.ngb.sampletenant.identity.application.user.port.PasswordHasher;
import dev.ngb.sampletenant.identity.application.session.port.SessionRepository;
import dev.ngb.sampletenant.identity.application.user.port.UserRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the plain-Java identity use cases and their adapters. */
@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {

    @Bean
    UserRepository userRepository(UserJdbcRepository rows, JdbcTemplate jdbc) {
        return new JdbcUserRepository(rows, jdbc);
    }

    @Bean
    SessionRepository sessionRepository(SessionJdbcRepository rows, JdbcTemplate jdbc) {
        return new JdbcSessionRepository(rows, jdbc);
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(
            UserRepository users, PasswordHasher hasher, Clock clock, IdGenerator ids, AccountListener listener) {
        return new RegisterUserUseCase(users, hasher, clock, ids, listener);
    }

    @Bean
    AuthenticateUseCase authenticateUseCase(
            UserRepository users,
            SessionRepository sessions,
            PasswordHasher hasher,
            Clock clock,
            IdGenerator ids,
            @Value("${sampletenant.identity.session-ttl:PT12H}") Duration sessionTtl) {
        return new AuthenticateUseCase(users, sessions, hasher, clock, ids, sessionTtl);
    }

    @Bean
    SessionService sessionService(UserRepository users, SessionRepository sessions, Clock clock) {
        return new SessionService(users, sessions, clock);
    }

    @Bean
    ManageUsersUseCase manageUsersUseCase(UserRepository users, AccountListener listener) {
        return new ManageUsersUseCase(users, listener);
    }
}
