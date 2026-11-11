package dev.ngb.core_billing.session.application.portal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Creates the two opaque tokens a session uses and the SHA-256 digest that is the only form ever stored. */
public class SessionTokens {

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random;

    public SessionTokens(SecureRandom random) {
        this.random = random;
    }

    /** 32 random bytes, base64url without padding: 256 bits of entropy. */
    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
