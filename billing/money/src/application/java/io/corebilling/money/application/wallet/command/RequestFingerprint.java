package io.corebilling.money.application.wallet.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A digest of a request's meaningful fields, so reusing an idempotency key for a different request is detected. */
public final class RequestFingerprint {

    private RequestFingerprint() {}

    public static String of(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            canonical.append(part == null ? "" : part).append('\u0000');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    public static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 255 || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("the idempotency key must be 1-255 printable characters");
        }
    }
}
