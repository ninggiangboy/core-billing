package dev.ngb.billing.webhook.adapter.delivery.http;

import dev.ngb.billing.webhook.application.delivery.port.WebhookSender;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * Posts a webhook with the safeguards an outbound call to a Tenant-chosen URL needs: addresses are checked
 * by the resolver the connection itself uses, redirects are never followed, connect and read timeouts and an
 * overall deadline bound the time, and only the first bytes of the response are read.
 */
public final class HttpComponentsWebhookSender implements WebhookSender, AutoCloseable {

    private static final int EXCERPT_BYTES = 1024;

    private final CloseableHttpClient client;
    private final long deadlineNanos;

    public HttpComponentsWebhookSender(
            boolean allowNonPublicTargets, int maxConnTotal, int maxConnPerRoute, Duration connectTimeout,
            Duration connectionRequestTimeout, Duration readTimeout, Duration deadline) {
        this.deadlineNanos = deadline.toNanos();
        ConnectionConfig connection = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                .setSocketTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new GuardedDnsResolver(allowNonPublicTargets))
                        .setDefaultConnectionConfig(connection)
                        .setMaxConnTotal(maxConnTotal)
                        .setMaxConnPerRoute(maxConnPerRoute)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout.toMillis()))
                        .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                        .build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .setUserAgent("CoreBilling-Webhooks/1")
                .build();
    }

    @Override
    public Result send(Request request) {
        long started = System.nanoTime();
        try {
            HttpPost post = new HttpPost(request.url());
            for (Map.Entry<String, String> header : request.headers().entrySet()) {
                post.setHeader(header.getKey(), header.getValue());
            }
            post.setEntity(new ByteArrayEntity(request.body(), null));
            // Closing the response without draining it discards the connection, so a huge body is never downloaded.
            try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
                String excerpt = excerpt(response.getEntity(), started);
                return new Result(response.getCode(), null, excerpt, elapsedMillis(started));
            }
        } catch (IOException | RuntimeException failure) {
            return new Result(null, describe(failure), null, elapsedMillis(started));
        }
    }

    private String excerpt(HttpEntity entity, long started) throws IOException {
        if (entity == null) {
            return null;
        }
        try (InputStream in = entity.getContent()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            while (out.size() < EXCERPT_BYTES && System.nanoTime() - started < deadlineNanos) {
                int read = in.read(buffer, 0, Math.min(buffer.length, EXCERPT_BYTES - out.size()));
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** Short, stable reasons: the log is shown to the Tenant, so it must not turn the call into a network probe. */
    private static String describe(Exception failure) {
        if (failure instanceof BlockedDestinationException) {
            return "blocked_destination";
        }
        if (failure instanceof UnknownHostException) {
            return "dns_failure";
        }
        if (failure instanceof ConnectTimeoutException || failure instanceof SocketTimeoutException
                || failure instanceof ConnectionRequestTimeoutException) {
            return "timeout";
        }
        if (failure instanceof SSLException) {
            return "tls_error";
        }
        if (failure instanceof IllegalArgumentException) {
            return "invalid_url";
        }
        return failure instanceof IOException ? "connection_failed" : "send_failed";
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
