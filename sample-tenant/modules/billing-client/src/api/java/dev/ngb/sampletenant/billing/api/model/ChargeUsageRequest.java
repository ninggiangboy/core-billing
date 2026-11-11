package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** Charges the not-yet-rated usage of one wallet in a period under a plan version. */
public record ChargeUsageRequest(
        String customerId,
        String walletId,
        String planId,
        Integer version,
        Instant from,
        Instant to) {}
