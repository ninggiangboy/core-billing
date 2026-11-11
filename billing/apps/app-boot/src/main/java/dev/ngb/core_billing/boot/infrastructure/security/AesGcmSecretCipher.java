package dev.ngb.core_billing.boot.infrastructure.security;

import dev.ngb.core_billing.webhook.application.shared.port.SecretCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Envelope encryption for secrets at rest. Each secret gets its own random data key; the data key is wrapped
 * with the master key, and both layers are AES-256-GCM with the context (the owning row's id) as associated
 * data, so a ciphertext copied to another row fails to decrypt.
 *
 * <p>Stored form: {@code v1.<master key id>.<wrapped data key>.<ciphertext>}, the binary parts base64url,
 * each being a 12-byte nonce followed by the GCM output. The webhook and payment modules each declare their own
 * port for it, so neither depends on the other's type. The master key id is stored so a later key rotation
 * can tell which key wrapped what; this class holds a single master key and refuses any other id.
 */
public class AesGcmSecretCipher implements SecretCipher, dev.ngb.core_billing.payment.application.stripe.port.SecretCipher {

    private static final String VERSION = "v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();
    private final SecretKeySpec masterKey;
    private final String masterKeyId;

    /** @param masterKeyBase64 a standard base64 encoding of exactly 32 random bytes */
    public AesGcmSecretCipher(String masterKeyBase64, String masterKeyId) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(masterKeyBase64 == null ? "" : masterKeyBase64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("billing.secrets.master-key must be base64", e);
        }
        if (key.length != 32) {
            throw new IllegalStateException("billing.secrets.master-key must decode to exactly 32 bytes (generate one with: openssl rand -base64 32)");
        }
        if (masterKeyId == null || !masterKeyId.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalStateException("billing.secrets.master-key-id must be 1-32 letters, digits, '-' or '_'");
        }
        this.masterKey = new SecretKeySpec(key, "AES");
        this.masterKeyId = masterKeyId;
    }

    @Override
    public String encrypt(String plaintext, String context) {
        byte[] dataKeyBytes = new byte[32];
        random.nextBytes(dataKeyBytes);
        byte[] wrapped = seal(masterKey, dataKeyBytes, aad("dek", context));
        byte[] sealed = seal(new SecretKeySpec(dataKeyBytes, "AES"), plaintext.getBytes(StandardCharsets.UTF_8), aad("data", context));
        return VERSION + "." + masterKeyId + "." + encoder.encodeToString(wrapped) + "." + encoder.encodeToString(sealed);
    }

    @Override
    public String decrypt(String ciphertext, String context) {
        String[] parts = ciphertext.split("\\.", -1);
        if (parts.length != 4 || !parts[0].equals(VERSION)) {
            throw new IllegalStateException("unrecognised secret format");
        }
        if (!parts[1].equals(masterKeyId)) {
            throw new IllegalStateException("the secret was protected with master key '" + parts[1] + "', which is not configured");
        }
        byte[] dataKey = open(masterKey, decoder.decode(parts[2]), aad("dek", context));
        byte[] plain = open(new SecretKeySpec(dataKey, "AES"), decoder.decode(parts[3]), aad("data", context));
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] aad(String purpose, String context) {
        return (purpose + ":" + context).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] seal(SecretKeySpec key, byte[] plain, byte[] aad) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] encrypted = cipher.doFinal(plain);
            return ByteBuffer.allocate(NONCE_BYTES + encrypted.length).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not encrypt a secret", e);
        }
    }

    private static byte[] open(SecretKeySpec key, byte[] sealed, byte[] aad) {
        if (sealed.length <= NONCE_BYTES) {
            throw new IllegalStateException("the stored secret is truncated");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES));
            cipher.updateAAD(aad);
            return cipher.doFinal(sealed, NONCE_BYTES, sealed.length - NONCE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not decrypt a secret: wrong key, wrong row, or tampered data", e);
        }
    }
}
