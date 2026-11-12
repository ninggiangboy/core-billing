package dev.ngb.sampletenant.identity.application.user.port;

/** Hashes and verifies passwords with a slow one-way function. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
