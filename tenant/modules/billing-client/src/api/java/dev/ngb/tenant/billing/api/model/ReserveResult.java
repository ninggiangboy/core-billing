package dev.ngb.tenant.billing.api.model;

/** The answer to reserve-from-estimate: the estimate that was held and the reservation itself. */
public record ReserveResult(Estimate estimate, Reservation reservation) {}
