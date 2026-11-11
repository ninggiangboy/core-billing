package dev.ngb.core_billing.sharedkernel.security;

/**
 * Protects a secret at rest (a Stripe key, a webhook signing secret). {@code context} is bound into the ciphertext
 * (the id of the row that holds it), so a ciphertext copied onto another row does not decrypt.
 */
public interface SecretCipher {

    String encrypt(String plaintext, String context);

    String decrypt(String ciphertext, String context);
}
