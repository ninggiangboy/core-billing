package dev.ngb.core_billing.webhook.adapter.http;

import java.net.UnknownHostException;

/** The host resolves to an address a webhook must not be sent to. */
final class BlockedDestinationException extends UnknownHostException {

    private static final long serialVersionUID = 1L;

    BlockedDestinationException(String host) {
        super("blocked destination: " + host);
    }
}
