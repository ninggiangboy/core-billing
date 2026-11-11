package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;
import java.util.List;

/** A Billing Session and the single-use portal URL that carries its exchange token. */
public record BillingSession(
        String id,
        String customerId,
        String portalUrl,
        List<String> permissions,
        Instant expiresAt) {}
