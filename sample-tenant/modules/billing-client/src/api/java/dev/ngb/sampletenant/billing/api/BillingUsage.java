package dev.ngb.sampletenant.billing.api;

import dev.ngb.sampletenant.billing.api.model.RecordedUsage;
import dev.ngb.sampletenant.billing.api.model.UsageEvent;
import dev.ngb.sampletenant.billing.api.model.UsageEventRequest;
import java.time.Instant;
import java.util.List;

/** Usage metering: one event or a batch, plus its history. */
public interface BillingUsage {

    RecordedUsage record(UsageEventRequest request);

    List<UsageEvent> history(String customerId, String metric, Instant from, Instant to, int limit);
}
