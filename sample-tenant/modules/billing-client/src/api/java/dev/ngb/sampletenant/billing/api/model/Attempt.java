package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** Why one provider attempt did not succeed. */
public record Attempt(String failureCode, String failureMessage, Instant occurredAt) {}
