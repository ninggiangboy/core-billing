package dev.ngb.sampletenant.identity.application;

import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.identity.domain.session.UserSession;
import dev.ngb.sampletenant.identity.application.user.port.PasswordHasher;
import dev.ngb.sampletenant.identity.application.session.port.SessionRepository;
import dev.ngb.sampletenant.identity.application.user.port.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class Fakes {

    private Fakes() {}

    static final class Users implements UserRepository {
        private final Map<UUID, User> byId = new LinkedHashMap<>();

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return byId.values().stream().filter(user -> user.email().equals(email)).findFirst();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public boolean emailExists(String email) {
            return findByEmail(email.trim().toLowerCase(Locale.ROOT)).isPresent();
        }

        @Override
        public void save(User user) {
            byId.put(user.id(), user);
        }
    }

    static final class Sessions implements SessionRepository {
        private final Map<String, UserSession> byHash = new LinkedHashMap<>();

        @Override
        public void save(UserSession session) {
            byHash.put(session.tokenHash(), session);
        }

        @Override
        public Optional<UserSession> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public void deleteByTokenHash(String tokenHash) {
            byHash.remove(tokenHash);
        }

        @Override
        public void deleteExpired(Instant now) {
            byHash.values().removeIf(session -> !now.isBefore(session.expiresAt()));
        }

        int size() {
            return byHash.size();
        }
    }

    static final class Hasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return hash(rawPassword).equals(passwordHash);
        }
    }
}
