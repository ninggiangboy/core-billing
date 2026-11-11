package dev.ngb.core_billing.sharedkernel.money;

import java.util.Objects;

/** An ISO 4217 currency with its minor-unit exponent (for example USD 2, VND 0). */
public record Currency(String code, int exponent) {

    public Currency {
        Objects.requireNonNull(code, "code");
        if (exponent < 0) {
            throw new IllegalArgumentException("exponent must not be negative");
        }
    }

    /** Resolves an ISO 4217 code; pseudo-currencies without a fraction-digit count are rejected. */
    public static Currency of(String code) {
        java.util.Currency iso;
        try {
            iso = java.util.Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown currency code: " + code, e);
        }
        if (iso.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("not a monetary currency: " + code);
        }
        return new Currency(iso.getCurrencyCode(), iso.getDefaultFractionDigits());
    }

    @Override
    public String toString() {
        return code;
    }
}
