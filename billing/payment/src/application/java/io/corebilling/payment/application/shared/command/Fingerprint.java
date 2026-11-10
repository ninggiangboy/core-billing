package io.corebilling.payment.application.shared.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Hashes the meaning of a request, so reusing an idempotency key for a different request is caught. */
public final class Fingerprint {

    private Fingerprint() {}

    public static String of(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200 || key.chars().anyMatch(c -> c <= 32 || c >= 127)) {
            throw new IllegalArgumentException("Idempotency-Key must be 1 to 200 printable characters");
        }
        return key;
    }
}
