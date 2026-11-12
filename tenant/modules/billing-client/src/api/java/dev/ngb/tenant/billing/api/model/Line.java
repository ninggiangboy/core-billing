package dev.ngb.tenant.billing.api.model;

/** One priced line. The amount is an exact decimal string. */
public record Line(String description, String metric, String quantity, String amount) {}
