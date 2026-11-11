package dev.ngb.core_billing.webhook.adapter.endpoint.crypto;

import dev.ngb.core_billing.webhook.application.endpoint.port.SigningKeys;
import java.security.SecureRandom;
import java.util.Base64;

/** A public key id and a 256-bit secret from the operating system's secure random source. */
public final class RandomSigningKeys implements SigningKeys {

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public Key generate() {
        byte[] id = new byte[9];
        byte[] secret = new byte[32];
        random.nextBytes(id);
        random.nextBytes(secret);
        return new Key("whk_" + encoder.encodeToString(id), "whsec_" + encoder.encodeToString(secret));
    }
}
