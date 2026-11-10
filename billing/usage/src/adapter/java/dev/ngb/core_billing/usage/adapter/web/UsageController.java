package dev.ngb.core_billing.usage.adapter.web;

import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.usage.api.usage.UsageEventId;
import dev.ngb.core_billing.usage.api.usage.UsageTotal;
import dev.ngb.core_billing.usage.application.command.RecordUsageUseCase;
import dev.ngb.core_billing.usage.application.query.UsageEventView;
import dev.ngb.core_billing.usage.application.query.UsageQueries;
import dev.ngb.core_billing.usage.domain.usage.UsageEvent;
import dev.ngb.core_billing.usage.domain.usage.UsageTrace;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usage reported by tenant systems. Each event carries its own {@code idempotencyKey}, so a retry of a single
 * event or of a whole batch never records anything twice. Quantities are decimal strings (or numbers) with up
 * to six decimal places, in the unit the metric's pricing plan defines.
 */
@RestController
@RequestMapping("/api/v1")
public class UsageController {

    public record UsageEventRequest(
            String customerId, String metric, String quantity, String unit, Instant occurredAt, String idempotencyKey, String resourceRef,
            String sourceEventId) {}

    public record BatchRequest(List<UsageEventRequest> events) {}

    public record UsageEventResponse(
            String id, String customerId, String metric, String quantity, String unit, Instant occurredAt, Instant recordedAt, String idempotencyKey,
            String tenantSystemId, String resourceRef, String sourceEventId) {
        static UsageEventResponse of(UsageEvent e) {
            return new UsageEventResponse(e.id().toString(), e.customerId().toString(), e.metric(), e.quantity().toPlainString(), e.unit(), e.occurredAt(),
                    e.recordedAt(), e.idempotencyKey(), e.trace().tenantSystemId() == null ? null : e.trace().tenantSystemId().toString(),
                    e.trace().resourceRef(), e.trace().sourceEventId());
        }

        static UsageEventResponse ofView(UsageEventView v) {
            return new UsageEventResponse(v.id().toString(), v.customerId().toString(), v.metric(), v.quantity().toPlainString(), v.unit(), v.occurredAt(),
                    v.recordedAt(), v.idempotencyKey(), v.tenantSystemId() == null ? null : v.tenantSystemId().toString(),
                    v.resourceRef(), v.sourceEventId());
        }
    }

    public record RecordedResponse(UsageEventResponse event, boolean replayed) {
        static RecordedResponse of(RecordUsageUseCase.Recorded recorded) {
            return new RecordedResponse(UsageEventResponse.of(recorded.event()), recorded.replayed());
        }
    }

    public record BatchResponse(List<RecordedResponse> results) {}

    public record TotalResponse(String metric, String unit, String quantity, long events) {
        static TotalResponse of(UsageTotal t) {
            return new TotalResponse(t.metric(), t.unit(), t.quantity().toPlainString(), t.events());
        }
    }

    private final RecordUsageUseCase record;
    private final UsageQueries queries;
    private final CurrentActor currentActor;

    public UsageController(RecordUsageUseCase record, UsageQueries queries, CurrentActor currentActor) {
        this.record = record;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    /** 201 for a newly recorded event, 200 when the same event was already recorded. */
    @PostMapping("/usage-events")
    @RequiresPermission(Permission.USAGE_RECORD)
    public ResponseEntity<RecordedResponse> recordOne(@RequestBody UsageEventRequest request) {
        RecordUsageUseCase.Recorded recorded = record.record(toItem(request, currentActor.get()), caller());
        return ResponseEntity.status(recorded.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(RecordedResponse.of(recorded));
    }

    /** All-or-nothing: if any event is invalid or conflicts, none is stored. Up to 500 events. */
    @PostMapping("/usage-events/batch")
    @RequiresPermission(Permission.USAGE_RECORD)
    public BatchResponse recordBatch(@RequestBody BatchRequest request) {
        if (request.events() == null) {
            throw new IllegalArgumentException("events is required");
        }
        Actor actor = currentActor.get();
        return new BatchResponse(record.record(request.events().stream().map(e -> toItem(e, actor)).toList(), caller()).stream()
                .map(RecordedResponse::of).toList());
    }

    /** Newest first. Pass the last {@code id} of a page as {@code before} for the next one. */
    @GetMapping("/usage-events")
    @RequiresPermission(Permission.USAGE_READ)
    public List<UsageEventResponse> history(
            @RequestParam String customerId, @RequestParam(required = false) String metric, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return queries.history(CustomerId.of(customerId), metric, from, to, before == null ? null : UsageEventId.of(before), limit, caller()).stream()
                .map(UsageEventResponse::ofView).toList();
    }

    /** Quantity and event count per metric over {@code [from, to)}, for rating and for invoices. */
    @GetMapping("/usage-aggregates")
    @RequiresPermission(Permission.USAGE_READ)
    public List<TotalResponse> aggregates(
            @RequestParam String customerId, @RequestParam(required = false) List<String> metric, @RequestParam Instant from, @RequestParam Instant to) {
        Set<String> metrics = metric == null ? Set.of() : new HashSet<>(metric);
        return queries.totals(CustomerId.of(customerId), metrics, from, to, caller()).stream().map(TotalResponse::of).toList();
    }

    private java.util.UUID caller() {
        var system = currentActor.get().tenantSystemId();
        return system == null ? null : system.value();
    }

    private static RecordUsageUseCase.Item toItem(UsageEventRequest r, Actor actor) {
        if (r == null) {
            throw new IllegalArgumentException("a usage event is required");
        }
        if (r.customerId() == null || r.quantity() == null) {
            throw new IllegalArgumentException("customerId and quantity are required");
        }
        return new RecordUsageUseCase.Item(CustomerId.of(r.customerId()), r.metric(), new BigDecimal(r.quantity().strip()), r.unit(), r.occurredAt(),
                r.idempotencyKey(), new UsageTrace(actor.tenantSystemId(), r.resourceRef(), r.sourceEventId()));
    }
}
