package dev.ngb.tenant.usage.application.usage.query;

import java.util.List;

/** Read side of local usage. */
public class UsageQueries {

    private final UsageReadRepository usage;

    public UsageQueries(UsageReadRepository usage) {
        this.usage = usage;
    }

    public List<UsageRecordView> forBillingCustomer(String billingCustomerId) {
        return usage.forBillingCustomer(billingCustomerId);
    }
}
