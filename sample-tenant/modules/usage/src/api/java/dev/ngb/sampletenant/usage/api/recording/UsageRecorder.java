package dev.ngb.sampletenant.usage.api.recording;

import java.time.Instant;
import java.util.UUID;

/** Records usage for an order locally and forwards it to Billing once. */
public interface UsageRecorder {

    record UsageCommand(
            UUID orderId,
            String billingCustomerId,
            String metric,
            String quantity,
            String unit,
            String resourceRef,
            Instant occurredAt) {}

    record Recorded(UUID id, String billingEventId) {}

    Recorded record(UsageCommand command);
}
