package dev.ngb.sampletenant.billing.api.client;

import dev.ngb.sampletenant.billing.api.model.Subscription;
import dev.ngb.sampletenant.billing.api.model.SubscriptionDetail;
import java.util.List;

/** Recurring subscriptions. Cancelling keeps the period that is already paid. */
public interface BillingSubscriptions {

    SubscriptionDetail subscribe(String idempotencyKey, String walletId, String planId);

    SubscriptionDetail subscription(String id);

    SubscriptionDetail cancel(String id);

    List<Subscription> subscriptions(String customerId, String walletId, String status, int limit);
}
