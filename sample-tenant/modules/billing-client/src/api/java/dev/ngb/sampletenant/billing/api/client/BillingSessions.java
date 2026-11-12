package dev.ngb.sampletenant.billing.api.client;

import dev.ngb.sampletenant.billing.api.model.BillingSession;
import java.util.List;

/** Creates the scoped portal session a customer uses to top up and read invoices. */
public interface BillingSessions {

    BillingSession create(String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds);

    void revoke(String id);
}
