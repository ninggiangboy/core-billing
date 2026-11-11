package dev.ngb.core_billing.webhook.application.endpoint.port;

/**
 * Protects signing secrets at rest. {@code context} is bound into the ciphertext (the endpoint id), so a
 * ciphertext copied onto another row does not decrypt.
 */
public interface SecretCipher {

    String encrypt(String plaintext, String context);

    String decrypt(String ciphertext, String context);
}
