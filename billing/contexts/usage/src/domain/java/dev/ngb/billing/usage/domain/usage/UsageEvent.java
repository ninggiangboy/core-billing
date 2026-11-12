package dev.ngb.billing.usage.domain.usage;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.usage.api.lookup.UsageEventId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A fact reported by a tenant system: this much of this metric was used by this customer at this time.
 * Immutable and never deleted; counting it twice is prevented by its idempotency key.
 */
public final class UsageEvent {

    public static final int MAX_FRACTION_DIGITS = 6;
    public static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999999999");
    /** Reporters' clocks drift; an event further ahead than this is a mistake. */
    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private static final Pattern METRIC = Pattern.compile("[a-z][a-z0-9._-]{0,63}");
    private static final Pattern UNIT = Pattern.compile("[A-Za-z][A-Za-z0-9._/-]{0,31}");

    private final UsageEventId id;
    private final TenantId tenantId;
    private final CustomerId customerId;
    private final String metric;
    private final BigDecimal quantity;
    private final String unit;
    private final Instant occurredAt;
    private final Instant recordedAt;
    private final String idempotencyKey;
    private final String requestHash;
    private final UsageTrace trace;

    private UsageEvent(
            UsageEventId id, TenantId tenantId, CustomerId customerId, String metric, BigDecimal quantity, String unit, Instant occurredAt,
            Instant recordedAt, String idempotencyKey, String requestHash, UsageTrace trace) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.metric = Objects.requireNonNull(metric, "metric");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.trace = Objects.requireNonNull(trace, "trace");
    }

    public static UsageEvent record(
            UsageEventId id, TenantId tenantId, CustomerId customerId, String metric, BigDecimal quantity, String unit, Instant occurredAt,
            String idempotencyKey, String requestHash, UsageTrace trace, Instant now) {
        requireMetric(metric);
        requireUnit(unit);
        BigDecimal normalized = normalize(quantity);
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (occurredAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw new IllegalArgumentException("occurredAt is in the future");
        }
        return new UsageEvent(id, tenantId, customerId, metric, normalized, unit, occurredAt, now, idempotencyKey, requestHash, trace);
    }

    public static UsageEvent restore(
            UsageEventId id, TenantId tenantId, CustomerId customerId, String metric, BigDecimal quantity, String unit, Instant occurredAt,
            Instant recordedAt, String idempotencyKey, String requestHash, UsageTrace trace) {
        return new UsageEvent(id, tenantId, customerId, metric, quantity, unit, occurredAt, recordedAt, idempotencyKey, requestHash, trace);
    }

    public static void requireMetric(String metric) {
        if (metric == null || !METRIC.matcher(metric).matches()) {
            throw new IllegalArgumentException("a metric is 1-64 characters: a lowercase letter, then lowercase letters, digits, '.', '_' or '-'");
        }
    }

    public static void requireUnit(String unit) {
        if (unit == null || !UNIT.matcher(unit).matches()) {
            throw new IllegalArgumentException("a unit is 1-32 characters: a letter, then letters, digits, '.', '_', '/' or '-'");
        }
    }

    /** The canonical decimal: positive, at most six fraction digits, no trailing zeros. */
    public static BigDecimal normalize(BigDecimal quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("quantity is required");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        BigDecimal stripped = quantity.stripTrailingZeros();
        if (stripped.scale() > MAX_FRACTION_DIGITS) {
            throw new IllegalArgumentException("quantity allows at most " + MAX_FRACTION_DIGITS + " decimal places");
        }
        if (stripped.compareTo(MAX_QUANTITY) > 0) {
            throw new IllegalArgumentException("quantity is too large");
        }
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public UsageEventId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public String metric() {
        return metric;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public String unit() {
        return unit;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }

    public UsageTrace trace() {
        return trace;
    }
}
