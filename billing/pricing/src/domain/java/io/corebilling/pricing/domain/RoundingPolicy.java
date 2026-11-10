package io.corebilling.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** How the exact total of a price becomes whole minor units. It is applied once, to the total, never to a line. */
public enum RoundingPolicy {
    /** Nearest, halves away from zero. */
    HALF_UP(RoundingMode.HALF_UP),
    /** Nearest, halves to the even neighbour (banker's rounding). */
    HALF_EVEN(RoundingMode.HALF_EVEN),
    /** Always up: the customer never pays less than the exact price. */
    UP(RoundingMode.CEILING),
    /** Always down: the customer never pays more than the exact price. */
    DOWN(RoundingMode.FLOOR);

    private final RoundingMode mode;

    RoundingPolicy(RoundingMode mode) {
        this.mode = mode;
    }

    /** @throws ArithmeticException when the result does not fit in a long */
    public long toMinorUnits(BigDecimal exact) {
        return exact.setScale(0, mode).longValueExact();
    }
}
