package dev.ngb.core_billing.pricing.application.rating.query;

import java.math.BigDecimal;

/** One priced line of a stored rating, as the read model exposes it. */
public record PriceLineView(String description, String metric, BigDecimal quantity, BigDecimal amount) {}
