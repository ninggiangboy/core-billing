package io.corebilling.webhook.application.delivery.command;

import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.webhook.domain.delivery.DeliveryAttempt;
import io.corebilling.webhook.domain.delivery.DeliveryPolicy;
import io.corebilling.webhook.domain.delivery.DeliveryStatus;
import io.corebilling.webhook.domain.delivery.WebhookHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleSupplier;
import io.corebilling.webhook.application.delivery.port.PayloadSigner;
import io.corebilling.webhook.application.delivery.port.WebhookDeliveryRepository;
import io.corebilling.webhook.application.delivery.port.WebhookSender;
import io.corebilling.webhook.application.shared.port.SecretCipher;
import io.corebilling.webhook.application.shared.port.WebhookSettings;

/**
 * One round of the sender worker for the current tenant: claim what is due, send it, record what came back.
 *
 * <p>The claim and the record are two short transactions; the HTTP calls happen between them, outside any
 * transaction and in parallel. A worker that dies in between leaves its rows claimed, and they become due
 * again when their lease runs out. Delivery is therefore at-least-once: a receiver must treat a repeated
 * {@code Billing-Event-Id} as a duplicate.
 */
public class DeliverWebhooksUseCase {

    public record Round(int claimed, int succeeded, int retrying, int dead) {
        public static final Round NONE = new Round(0, 0, 0, 0);
    }

    private final UnitOfWork unitOfWork;
    private final WebhookDeliveryRepository deliveries;
    private final SecretCipher cipher;
    private final PayloadSigner signer;
    private final WebhookSender sender;
    private final WebhookSettings settings;
    private final Clock clock;
    private final DoubleSupplier random;

    public DeliverWebhooksUseCase(
            UnitOfWork unitOfWork, WebhookDeliveryRepository deliveries, SecretCipher cipher, PayloadSigner signer, WebhookSender sender,
            WebhookSettings settings, Clock clock, DoubleSupplier random) {
        this.unitOfWork = unitOfWork;
        this.deliveries = deliveries;
        this.cipher = cipher;
        this.signer = signer;
        this.sender = sender;
        this.settings = settings;
        this.clock = clock;
        this.random = random;
    }

    public Round runOnce() {
        DeliveryPolicy policy = settings.policy();
        List<ClaimedDelivery> claimed = unitOfWork.transactional(() -> {
            Instant now = clock.now();
            deliveries.killExhausted(now, policy.maxAttempts());
            return deliveries.claimDue(now, settings.batchSize(), policy.lease());
        });
        if (claimed.isEmpty()) {
            return Round.NONE;
        }
        List<Sent> results = sendAll(claimed);
        return unitOfWork.transactional(() -> {
            int succeeded = 0;
            int retrying = 0;
            int dead = 0;
            for (Sent sent : results) {
                DeliveryAttempt attempt = sent.attempt();
                int attemptsMade = sent.claimed().delivery().attempts();
                DeliveryStatus status;
                Instant next = clock.now();
                Instant completed = null;
                if (attempt.succeeded()) {
                    status = DeliveryStatus.SUCCEEDED;
                    completed = attempt.attemptedAt();
                    succeeded++;
                } else if (policy.exhausted(attemptsMade)) {
                    status = DeliveryStatus.DEAD;
                    completed = attempt.attemptedAt();
                    dead++;
                } else {
                    status = DeliveryStatus.PENDING;
                    next = clock.now().plus(policy.retryDelay(attemptsMade, random.getAsDouble()));
                    retrying++;
                }
                deliveries.recordAttempt(sent.claimed().delivery().id(), attemptsMade, attempt, status, next, completed);
            }
            return new Round(results.size(), succeeded, retrying, dead);
        });
    }

    private record Sent(ClaimedDelivery claimed, DeliveryAttempt attempt) {}

    /** Sends in parallel on virtual threads; the sends touch no database, so they need no tenant scope. */
    private List<Sent> sendAll(List<ClaimedDelivery> claimed) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Sent>> futures = new ArrayList<>();
            for (ClaimedDelivery item : claimed) {
                futures.add(executor.submit(() -> send(item)));
            }
            List<Sent> results = new ArrayList<>();
            for (Future<Sent> future : futures) {
                try {
                    results.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while sending webhooks", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("a webhook send failed unexpectedly", e.getCause());
                }
            }
            return results;
        }
    }

    private Sent send(ClaimedDelivery item) {
        Instant startedAt = clock.now();
        WebhookSender.Result result;
        try {
            long timestamp = startedAt.getEpochSecond();
            byte[] body = item.delivery().body().getBytes(StandardCharsets.UTF_8);
            String secret = cipher.decrypt(item.secretCiphertext(), item.delivery().endpointId().toString());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put(WebhookHeaders.EVENT_ID, item.delivery().eventId().toString());
            headers.put(WebhookHeaders.EVENT_TYPE, item.delivery().eventType());
            headers.put(WebhookHeaders.TIMESTAMP, Long.toString(timestamp));
            headers.put(WebhookHeaders.KEY_ID, item.keyId());
            headers.put(WebhookHeaders.SIGNATURE, signer.sign(secret, timestamp, body));
            headers.put(WebhookHeaders.ATTEMPT, Integer.toString(item.delivery().attempts()));
            result = sender.send(new WebhookSender.Request(item.url(), headers, body));
        } catch (RuntimeException failure) {
            result = new WebhookSender.Result(null, "could not prepare the request: " + failure.getClass().getSimpleName(), null, 0);
        }
        return new Sent(item, new DeliveryAttempt(
                item.delivery().attempts(), result.statusCode(), result.error(), result.responseExcerpt(), result.latencyMs(), startedAt));
    }
}
