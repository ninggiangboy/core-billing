package dev.ngb.tenant.shared.money;

import java.util.regex.Pattern;

/** An amount in minor units (cents, dong) of one ISO 4217 currency. */
public record Money(long minorUnits, String currency) {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    public Money {
        if (!CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code: " + currency);
        }
    }

    public static Money of(long minorUnits, String currency) {
        return new Money(minorUnits, currency);
    }
}
