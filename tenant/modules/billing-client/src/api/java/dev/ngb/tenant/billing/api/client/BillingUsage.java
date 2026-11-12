package dev.ngb.tenant.billing.api.client;

import dev.ngb.tenant.billing.api.model.RecordedUsage;
import dev.ngb.tenant.billing.api.model.UsageEvent;
import dev.ngb.tenant.billing.api.model.UsageEventRequest;
import java.time.Instant;
import java.util.List;

/** Usage metering: one event or a batch, plus its history. */
public interface BillingUsage {

    RecordedUsage record(UsageEventRequest request);

    List<UsageEvent> history(String customerId, String metric, Instant from, Instant to, int limit);
}
