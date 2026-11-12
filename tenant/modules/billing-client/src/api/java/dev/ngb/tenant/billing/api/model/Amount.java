package dev.ngb.tenant.billing.api.model;

import dev.ngb.tenant.shared.money.Money;

/** Money as Billing's REST contract carries it: whole minor units plus an ISO 4217 code. */
public record Amount(long amount, String currency) {

    public Money money() {
        return Money.of(amount, currency);
    }

    public static Amount of(Money money) {
        return new Amount(money.minorUnits(), money.currency());
    }
}
