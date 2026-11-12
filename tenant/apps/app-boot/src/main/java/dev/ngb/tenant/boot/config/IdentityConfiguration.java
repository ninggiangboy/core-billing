package dev.ngb.tenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.identity.adapter.session.persistence.JooqSessionRepository;
import dev.ngb.tenant.identity.adapter.user.persistence.JooqUserReadRepository;
import dev.ngb.tenant.identity.adapter.user.persistence.JooqUserRepository;
import dev.ngb.tenant.identity.adapter.user.security.BCryptPasswordHasher;
import dev.ngb.tenant.identity.api.account.AccountListener;
import dev.ngb.tenant.identity.application.session.command.AuthenticateUseCase;
import dev.ngb.tenant.identity.application.session.port.SessionRepository;
import dev.ngb.tenant.identity.application.session.service.SessionService;
import dev.ngb.tenant.identity.application.user.command.ManageUsersUseCase;
import dev.ngb.tenant.identity.application.user.command.RegisterUserUseCase;
import dev.ngb.tenant.identity.application.user.port.PasswordHasher;
import dev.ngb.tenant.identity.application.user.port.UserRepository;
import dev.ngb.tenant.identity.application.user.query.UserReadRepository;
import java.time.Duration;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the plain-Java identity use cases and their adapters. */
@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {

    @Bean
    UserRepository userRepository(DSLContext dsl) {
        return new JooqUserRepository(dsl);
    }

    @Bean
    UserReadRepository userReadRepository(DSLContext dsl) {
        return new JooqUserReadRepository(dsl);
    }

    @Bean
    SessionRepository sessionRepository(DSLContext dsl) {
        return new JooqSessionRepository(dsl);
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
            @Value("${tenant.identity.session-ttl:PT12H}") Duration sessionTtl) {
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
