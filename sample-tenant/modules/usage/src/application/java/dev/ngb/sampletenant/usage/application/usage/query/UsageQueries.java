package dev.ngb.sampletenant.usage.application.usage.query;

import dev.ngb.sampletenant.usage.application.usage.port.UsageRepository;
import dev.ngb.sampletenant.usage.domain.usage.UsageRecord;
import java.util.List;

/** Read side of local usage. */
public class UsageQueries {

    private final UsageRepository usage;

    public UsageQueries(UsageRepository usage) {
        this.usage = usage;
    }

    public List<UsageRecord> forBillingCustomer(String billingCustomerId) {
        return usage.findByBillingCustomerId(billingCustomerId);
    }
}
