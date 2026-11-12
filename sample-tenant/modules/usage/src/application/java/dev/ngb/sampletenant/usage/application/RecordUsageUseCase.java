package dev.ngb.sampletenant.usage.application;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.billing.api.BillingUsage;
import dev.ngb.sampletenant.billing.api.model.RecordedUsage;
import dev.ngb.sampletenant.billing.api.model.UsageEventRequest;
import dev.ngb.sampletenant.usage.api.UsageRecorder;
import dev.ngb.sampletenant.usage.application.port.UsageRepository;
import dev.ngb.sampletenant.usage.domain.UsageRecord;

/**
 * Scenario 5: usage is recorded locally and sent to Billing with a deterministic idempotency key,
 * so a retried execution records the same event once.
 */
public class RecordUsageUseCase implements UsageRecorder {

    private final UsageRepository usage;
    private final BillingUsage billing;
    private final Clock clock;
    private final IdGenerator ids;

    public RecordUsageUseCase(UsageRepository usage, BillingUsage billing, Clock clock, IdGenerator ids) {
        this.usage = usage;
        this.billing = billing;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public Recorded record(UsageCommand command) {
        String key = key(command);
        return usage.findByOrderIdAndMetric(command.orderId(), command.metric())
                .map(existing -> new Recorded(existing.id(), existing.billingEventId()))
                .orElseGet(() -> forward(command, key));
    }

    private Recorded forward(UsageCommand command, String key) {
        RecordedUsage recorded = billing.record(new UsageEventRequest(
                command.billingCustomerId(),
                command.metric(),
                command.quantity(),
                command.unit(),
                command.occurredAt(),
                key,
                command.resourceRef(),
                command.orderId().toString()));
        UsageRecord row = UsageRecord.recorded(
                ids,
                command.orderId(),
                command.billingCustomerId(),
                command.metric(),
                command.quantity(),
                command.unit(),
                recorded.event().id(),
                key,
                command.occurredAt(),
                clock.now());
        usage.save(row);
        return new Recorded(row.id(), recorded.event().id());
    }

    private static String key(UsageCommand command) {
        return "usage:" + command.orderId() + ":" + command.metric();
    }
}
