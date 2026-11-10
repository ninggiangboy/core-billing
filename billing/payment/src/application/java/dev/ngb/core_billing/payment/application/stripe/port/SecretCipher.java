package dev.ngb.core_billing.payment.application.stripe.port;

/**
 * Protects Stripe keys at rest. {@code context} is bound into the ciphertext (the configuration id), so a
 * ciphertext copied onto another row does not decrypt.
 */
public interface SecretCipher {

    String encrypt(String plaintext, String context);

    String decrypt(String ciphertext, String context);
}
