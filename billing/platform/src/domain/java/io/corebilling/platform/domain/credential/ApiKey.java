package io.corebilling.platform.domain.credential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The format of a Tenant System credential: {@code cbk_<key-id>_<secret>}. The key id (8 random bytes)
 * finds the credential; the secret (32 random bytes) proves possession and is stored only as a SHA-256
 * digest. Both parts are hex, so the separator can never appear inside them.
 */
public final class ApiKey {

    public static final String PREFIX = "cbk_";
    private static final Pattern FORMAT = Pattern.compile("^cbk_([0-9a-f]{16})_([0-9a-f]{64})$");

    /** A freshly generated key. {@code plaintext} is shown to the user once and never stored. */
    public record Generated(String keyId, String secret, String plaintext) {
        @Override
        public String toString() {
            return "Generated[keyId=" + keyId + "]";
        }
    }

    public record Parsed(String keyId, String secret) {
        @Override
        public String toString() {
            return "Parsed[keyId=" + keyId + "]";
        }
    }

    private ApiKey() {}

    public static Generated generate(SecureRandom random) {
        byte[] id = new byte[8];
        byte[] secret = new byte[32];
        random.nextBytes(id);
        random.nextBytes(secret);
        HexFormat hex = HexFormat.of();
        String keyId = hex.formatHex(id);
        String secretHex = hex.formatHex(secret);
        return new Generated(keyId, secretHex, PREFIX + keyId + "_" + secretHex);
    }

    public static Optional<Parsed> parse(String presented) {
        if (presented == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(presented);
        return matcher.matches() ? Optional.of(new Parsed(matcher.group(1), matcher.group(2))) : Optional.empty();
    }

    public static byte[] hash(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
