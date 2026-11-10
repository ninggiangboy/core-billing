package io.corebilling.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.boot.infrastructure.AesGcmSecretCipher;
import io.corebilling.support.PostgresContainerConfig;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmSecretCipherTest {

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(PostgresContainerConfig.TEST_MASTER_KEY, "k1");

    @Test
    void roundTripsAndNeverStoresThePlaintext() {
        String stored = cipher.encrypt("whsec_super-secret", "endpoint-1");
        assertThat(stored).startsWith("v1.k1.").doesNotContain("super-secret");
        assertThat(cipher.decrypt(stored, "endpoint-1")).isEqualTo("whsec_super-secret");
    }

    @Test
    void everyEncryptionIsDifferent() {
        assertThat(cipher.encrypt("same", "ctx")).isNotEqualTo(cipher.encrypt("same", "ctx"));
    }

    @Test
    void aCiphertextMovedToAnotherRowDoesNotDecrypt() {
        String stored = cipher.encrypt("whsec_x", "endpoint-1");
        assertThatThrownBy(() -> cipher.decrypt(stored, "endpoint-2")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperingIsDetected() {
        String stored = cipher.encrypt("whsec_x", "endpoint-1");
        String[] parts = stored.split("\\.");
        byte[] data = Base64.getUrlDecoder().decode(parts[3]);
        data[data.length - 1] ^= 1;
        parts[3] = Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        assertThatThrownBy(() -> cipher.decrypt(String.join(".", parts), "endpoint-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherMasterKeyCannotDecrypt() {
        AesGcmSecretCipher other = new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[32]), "k1");
        String stored = cipher.encrypt("whsec_x", "ctx");
        assertThatThrownBy(() -> other.decrypt(stored, "ctx")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnknownMasterKeyIdIsNamedInTheError() {
        AesGcmSecretCipher rotated = new AesGcmSecretCipher(PostgresContainerConfig.TEST_MASTER_KEY, "k2");
        assertThatThrownBy(() -> rotated.decrypt(cipher.encrypt("x", "c"), "c")).hasMessageContaining("'k1'");
    }

    @Test
    void refusesAMisconfiguredMasterKey() {
        assertThatThrownBy(() -> new AesGcmSecretCipher("not base64 !!", "k1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[16]), "k1")).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new AesGcmSecretCipher(null, "k1")).isInstanceOf(IllegalStateException.class);
    }
}
