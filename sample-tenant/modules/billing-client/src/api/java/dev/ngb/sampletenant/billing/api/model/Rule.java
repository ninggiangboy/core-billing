package dev.ngb.sampletenant.billing.api.model;

import java.util.List;

/** The price rule of one plan version. Money-bearing values are exact decimal strings. */
public record Rule(
        String model,
        String metric,
        String unit,
        String unitPrice,
        String increment,
        String mode,
        List<Tier> tiers,
        String executionFee,
        String fixedPrice,
        List<Rule> components) {}
