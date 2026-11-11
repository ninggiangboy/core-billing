package dev.ngb.sampletenant.billing.api.model;

/** One graduated or volume tier. A zero first tier is an included allowance. */
public record Tier(String upTo, String unitPrice) {}
