package dev.ngb.tenant.usage.application.usage.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.billing.api.client.BillingUsage;
import dev.ngb.tenant.billing.api.model.RecordedUsage;
import dev.ngb.tenant.billing.api.model.UsageEventRequest;
import dev.ngb.tenant.usage.api.recording.UsageRecorder;
import dev.ngb.tenant.usage.application.usage.port.UsageRepository;
import dev.ngb.tenant.usage.domain.usage.UsageRecord;

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
