package dev.ngb.billing.platform.api.idempotency;

import dev.ngb.billing.platform.api.error.IdempotencyConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The digest of a request's meaningful fields, so reusing an idempotency key for a different request is caught. */
public final class Fingerprint {

    /** Longest idempotency key a client may send. */
    public static final int MAX_KEY = 255;

    private Fingerprint() {}

    /**
     * The digest of {@code parts}; each is followed by a NUL so that {@code ("a","b")} and {@code ("ab","")}
     * cannot collide. A null part reads as empty.
     */
    public static String of(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            canonical.append(part == null ? "" : part).append('\u0000');
        }
        return sha256(canonical.toString());
    }

    /** The hex SHA-256 of the UTF-8 bytes of {@code canonical}. */
    public static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /**
     * Confirms that a request replaying {@code key} carries the same content as the request that created the row.
     *
     * @throws IdempotencyConflictException when the two hashes differ
     */
    public static void requireSameRequest(String scope, String key, String storedHash, String requestHash) {
        if (!storedHash.equals(requestHash)) {
            throw new IdempotencyConflictException(scope, key);
        }
    }

    /** @return {@code key} when it is 1-255 printable ASCII characters */
    public static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-255 printable characters");
        }
        return key;
    }
}
