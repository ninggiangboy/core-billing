package io.corebilling.pricing.domain.metric;

import java.math.BigDecimal;
import java.util.Objects;

/** What was used of one metric: the summed quantity and how many events it came from. */
public record Measure(BigDecimal quantity, long events) {

    public static final Measure NONE = new Measure(BigDecimal.ZERO, 0);

    public Measure {
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() < 0 || events < 0) {
            throw new IllegalArgumentException("a measure cannot be negative");
        }
    }
}
