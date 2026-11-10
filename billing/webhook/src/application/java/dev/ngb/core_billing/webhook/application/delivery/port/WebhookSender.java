package dev.ngb.core_billing.webhook.application.delivery.port;

import java.util.Map;

/**
 * Performs one HTTP POST. It never throws: a refused destination, a timeout or a bad status is a result, so
 * the caller records it like any other failed attempt. Implementations must not follow redirects.
 */
public interface WebhookSender {

    record Request(String url, Map<String, String> headers, byte[] body) {}

    /** {@code statusCode} is null when no response arrived; {@code error} then says why. */
    record Result(Integer statusCode, String error, String responseExcerpt, long latencyMs) {}

    Result send(Request request);
}
