package dev.ngb.billing.sharedkernel.money;

/**
 * An amount as it appears in a request or a response: whole minor units (cents, dong) and an ISO 4217
 * currency code. The wire shape is shared by every context that speaks about money.
 */
public record MoneyDto(long amount, String currency) {

    public static MoneyDto of(Money money) {
        return new MoneyDto(money.minorUnits(), money.currency().code());
    }

    public Money toMoney() {
        if (currency == null) {
            throw new IllegalArgumentException("currency is required");
        }
        return Money.of(amount, Currency.of(currency));
    }
}
