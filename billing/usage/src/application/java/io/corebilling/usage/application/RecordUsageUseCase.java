package io.corebilling.usage.application;

import io.corebilling.customer.api.lookup.CustomerLookup;
import io.corebilling.customer.api.lookup.CustomerSummary;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.IdempotencyConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.usage.api.UsageEventId;
import io.corebilling.usage.domain.UsageEvent;
import io.corebilling.usage.domain.UsageTrace;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Records usage, once per idempotency key. A request is validated as a whole before anything is stored, and
 * stored in one transaction, so a batch is all-or-nothing. Repeating an event with the same key and content is
 * a no-op that returns the stored event; the same key with different content is a conflict.
 */
public class RecordUsageUseCase {

    public static final int MAX_BATCH = 500;

    /** One event to record. */
    public record Item(CustomerId customerId, String metric, BigDecimal quantity, String unit, Instant occurredAt, String idempotencyKey, UsageTrace trace) {}

    public record Recorded(UsageEvent event, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final UsageEventRepository events;
    private final CustomerLookup customers;
    private final MetricUnits metricUnits;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public RecordUsageUseCase(
            UnitOfWork unitOfWork, UsageEventRepository events, CustomerLookup customers, MetricUnits metricUnits, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.customers = customers;
        this.metricUnits = metricUnits;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Recorded record(Item item) {
        return record(List.of(item)).get(0);
    }

    /** @return one result per item, in order */
    public List<Recorded> record(List<Item> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("at least one usage event is required");
        }
        if (items.size() > MAX_BATCH) {
            throw new IllegalArgumentException("a batch holds at most " + MAX_BATCH + " usage events");
        }
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            Instant now = clock.now();
            Map<CustomerId, CustomerSummary> knownCustomers = new HashMap<>();
            List<UsageEvent> prepared = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                try {
                    prepared.add(prepare(items.get(i), tenantId, knownCustomers, now));
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException("usage event " + i + ": " + invalid.getMessage(), invalid);
                }
            }
            List<Recorded> results = new ArrayList<>();
            for (UsageEvent event : prepared) {
                results.add(store(event));
            }
            return results;
        });
    }

    private UsageEvent prepare(Item item, TenantId tenantId, Map<CustomerId, CustomerSummary> knownCustomers, Instant now) {
        RecordKey.require(item.idempotencyKey());
        if (item.customerId() == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        UsageEvent.requireMetric(item.metric());
        UsageEvent.requireUnit(item.unit());
        BigDecimal quantity = UsageEvent.normalize(item.quantity());
        String expected = metricUnits.unitOf(item.metric())
                .orElseThrow(() -> new IllegalArgumentException("unknown metric '" + item.metric() + "': no pricing plan measures it"));
        if (!expected.equals(item.unit())) {
            throw new IllegalArgumentException("metric '" + item.metric() + "' is measured in '" + expected + "', not '" + item.unit() + "'");
        }
        CustomerSummary customer = knownCustomers.computeIfAbsent(item.customerId(),
                id -> customers.find(id).orElseThrow(() -> new NotFoundException("customer not found")));
        if (!customer.active()) {
            throw new ConflictException("the customer is deactivated");
        }
        String hash = fingerprint(item.customerId(), item.metric(), quantity.toPlainString(), item.unit(), item.occurredAt(),
                item.trace().tenantSystemId(), item.trace().resourceRef(), item.trace().sourceEventId());
        return UsageEvent.record(new UsageEventId(ids.newId()), tenantId, item.customerId(), item.metric(), quantity, item.unit(), item.occurredAt(),
                item.idempotencyKey(), hash, item.trace(), now);
    }

    private Recorded store(UsageEvent event) {
        if (events.addIfAbsent(event)) {
            return new Recorded(event, false);
        }
        UsageEvent existing = events.findByIdempotencyKey(event.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("usage event vanished after a key conflict"));
        if (!existing.requestHash().equals(event.requestHash())) {
            throw new IdempotencyConflictException("usage", event.idempotencyKey());
        }
        return new Recorded(existing, true);
    }

    private static String fingerprint(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            canonical.append(part == null ? "" : part).append('\u0000');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
