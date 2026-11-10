package io.corebilling.webhook.application.shared.port;

/** Creates signing secrets. The key id is public and tells a receiver which secret signed a delivery. */
public interface SigningKeys {

    record Key(String keyId, String secret) {}

    Key generate();
}
