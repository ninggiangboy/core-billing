package dev.ngb.core_billing.pricing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Validation and arithmetic shared by the price rules. */
final class Prices {

    static final int MAX_SCALE = 10;
    static final BigDecimal MAX_PRICE = new BigDecimal("1000000000000000");
    private static final Pattern METRIC = Pattern.compile("[a-z][a-z0-9._-]{0,63}");
    private static final Pattern UNIT = Pattern.compile("[A-Za-z][A-Za-z0-9._/-]{0,31}");

    private Prices() {}

    /** A price in minor units: not negative, not absurdly large, at most ten decimal places. */
    static BigDecimal price(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        if (value.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException(name + " is too large");
        }
        if (value.stripTrailingZeros().scale() > MAX_SCALE) {
            throw new IllegalArgumentException("%s allows at most %s decimal places".formatted(name, MAX_SCALE));
        }
        return value;
    }

    static String metric(String metric) {
        if (metric == null || !METRIC.matcher(metric).matches()) {
            throw new IllegalArgumentException("a metric is 1-64 characters: a lowercase letter, then lowercase letters, digits, '.', '_' or '-'");
        }
        return metric;
    }

    static String unit(String unit) {
        if (unit == null || !UNIT.matcher(unit).matches()) {
            throw new IllegalArgumentException("a unit is 1-32 characters: a letter, then letters, digits, '.', '_', '/' or '-'");
        }
        return unit;
    }

    static BigDecimal times(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice);
    }

    static boolean isWhole(BigDecimal quantity) {
        return quantity.stripTrailingZeros().scale() <= 0;
    }

    /** Rounds {@code quantity} up to a whole multiple of {@code step}. */
    static BigDecimal ceilToMultiple(BigDecimal quantity, BigDecimal step) {
        return quantity.divide(step, 0, RoundingMode.CEILING).multiply(step);
    }

    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
