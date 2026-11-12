package dev.ngb.tenant.billing.api.model;

import java.time.Instant;

/** A Billing Customer, as returned by the customer endpoints. */
public record Customer(String id, String displayName, String email, String status, Instant createdAt) {}
