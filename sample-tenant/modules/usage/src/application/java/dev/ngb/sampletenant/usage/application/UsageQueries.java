package dev.ngb.sampletenant.usage.application;

import dev.ngb.sampletenant.usage.application.port.UsageRepository;
import dev.ngb.sampletenant.usage.domain.UsageRecord;
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
