package dev.ngb.sampletenant.billing.api.model;

import java.util.List;

/** A subscription with its renewal history. */
public record SubscriptionDetail(Subscription subscription, List<SubscriptionEvent> history) {}
