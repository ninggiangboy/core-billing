package dev.ngb.billing.pricing.api.plan;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only recurring prices for the contexts that bill a plan on a schedule. It answers within the current tenant
 * scope, so another tenant's plan is simply absent. It never reads or writes usage: only the fixed part of a plan.
 */
public interface PlanPricing {

    /**
     * What a customer subscribes to: present only for an active plan whose version in force at {@code at} has a
     * fixed amount per billing cycle (a Subscription or Hybrid plan).
     */
    Optional<SubscriptionTerms> subscriptionTerms(UUID planId, Instant at);

    /**
     * The recurring amount of the billing period that starts at {@code at}, under the plan version in force then.
     * A retired plan still answers, so subscriptions already running keep renewing; empty when no version is in
     * force at {@code at} or the version has no fixed part.
     */
    Optional<PeriodPrice> periodPrice(UUID planId, Instant at);
}
