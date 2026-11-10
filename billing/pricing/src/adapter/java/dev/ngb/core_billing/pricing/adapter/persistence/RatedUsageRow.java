package dev.ngb.core_billing.pricing.adapter.persistence;

import java.util.UUID;

/** One row of the usage/charge link, as the write side checks which events are already rated. */
record RatedUsageRow(UUID usageEventId, UUID ratingId) {}
