package dev.ngb.tenant.usage.application.usage.query;

import java.util.List;

/** Read port for recorded usage; it selects straight into views. */
public interface UsageReadRepository {

    List<UsageRecordView> forBillingCustomer(String billingCustomerId);
}
