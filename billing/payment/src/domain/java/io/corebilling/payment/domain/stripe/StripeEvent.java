package io.corebilling.payment.domain.stripe;

import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * A verified Stripe event, stored before it is acted on. The payload is exactly what Stripe sent, so processing
 * can be repeated from it. {@code attempts} counts how often processing was tried.
 */
public final class StripeEvent {

    private final StoredEventId id;
    private final TenantId tenantId;
    private final StripeConfigurationId configurationId;
    private final String stripeAccount;
    private final String eventId;
    private final String type;
    private final boolean livemode;
    private final String payload;
    private EventStatus status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private final Instant receivedAt;
    private Instant processedAt;

    private StripeEvent(
            StoredEventId id, TenantId tenantId, StripeConfigurationId configurationId, String stripeAccount, String eventId, String type,
            boolean livemode, String payload, EventStatus status, int attempts, Instant nextAttemptAt, String lastError, Instant receivedAt,
            Instant processedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.configurationId = Objects.requireNonNull(configurationId, "configurationId");
        this.stripeAccount = Objects.requireNonNull(stripeAccount, "stripeAccount");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.type = Objects.requireNonNull(type, "type");
        this.livemode = livemode;
        this.payload = Objects.requireNonNull(payload, "payload");
        this.status = Objects.requireNonNull(status, "status");
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.processedAt = processedAt;
    }

    public static StripeEvent received(
            StoredEventId id, TenantId tenantId, StripeConfigurationId configurationId, String stripeAccount, String eventId, String type,
            boolean livemode, String payload, Instant now) {
        return new StripeEvent(id, tenantId, configurationId, stripeAccount, eventId, type, livemode, payload, EventStatus.RECEIVED, 0, null, null,
                now, null);
    }

    public static StripeEvent restore(
            StoredEventId id, TenantId tenantId, StripeConfigurationId configurationId, String stripeAccount, String eventId, String type,
            boolean livemode, String payload, EventStatus status, int attempts, Instant nextAttemptAt, String lastError, Instant receivedAt,
            Instant processedAt) {
        return new StripeEvent(id, tenantId, configurationId, stripeAccount, eventId, type, livemode, payload, status, attempts, nextAttemptAt,
                lastError, receivedAt, processedAt);
    }

    /** @return the number of this attempt, counted from 1 */
    public int beginAttempt() {
        return ++attempts;
    }

    public void processed(Instant now) {
        finish(EventStatus.PROCESSED, null, now);
    }

    public void ignored(String why, Instant now) {
        finish(EventStatus.IGNORED, why, now);
    }

    public void dead(String why, Instant now) {
        finish(EventStatus.DEAD, why, now);
    }

    /** Processing could not finish yet: retry at {@code retryAt}, or give up when {@code retryAt} is null. */
    public void failed(String why, Instant retryAt, Instant now) {
        if (retryAt == null) {
            dead(why, now);
            return;
        }
        this.status = EventStatus.FAILED;
        this.lastError = clip(why);
        this.nextAttemptAt = retryAt;
    }

    /** A person asked for another go at an event that gave up. */
    public void reopen(Instant now) {
        if (status != EventStatus.DEAD && status != EventStatus.FAILED) {
            throw new IllegalStateException("only a failed or dead event can be retried");
        }
        this.status = EventStatus.FAILED;
        this.nextAttemptAt = now;
        this.processedAt = null;
    }

    private void finish(EventStatus next, String note, Instant now) {
        this.status = next;
        this.lastError = next == EventStatus.PROCESSED ? null : clip(note);
        this.nextAttemptAt = null;
        this.processedAt = now;
    }

    private static String clip(String text) {
        return text == null || text.length() <= 1000 ? text : text.substring(0, 1000);
    }

    public StoredEventId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public StripeConfigurationId configurationId() {
        return configurationId;
    }

    public String stripeAccount() {
        return stripeAccount;
    }

    public String eventId() {
        return eventId;
    }

    public String type() {
        return type;
    }

    public boolean livemode() {
        return livemode;
    }

    public String payload() {
        return payload;
    }

    public EventStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public String lastError() {
        return lastError;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public Instant processedAt() {
        return processedAt;
    }
}
