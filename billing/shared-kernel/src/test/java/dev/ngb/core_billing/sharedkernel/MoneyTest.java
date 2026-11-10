package dev.ngb.core_billing.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency USD = Currency.of("USD");
    private static final Currency VND = Currency.of("VND");

    @Test
    void currencyCarriesIsoExponent() {
        assertThat(USD.exponent()).isEqualTo(2);
        assertThat(VND.exponent()).isZero();
    }

    @Test
    void rejectsUnknownAndPseudoCurrencies() {
        assertThatThrownBy(() -> Currency.of("ZZZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Currency.of("XXX")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Currency.of("usd")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsAndSubtractsWithinOneCurrency() {
        Money total = Money.of(500, USD).plus(Money.of(250, USD)).minus(Money.of(100, USD));
        assertThat(total).isEqualTo(Money.of(650, USD));
        assertThat(total.negate().isNegative()).isTrue();
        assertThat(Money.zero(USD).isZero()).isTrue();
    }

    @Test
    void rejectsMixedCurrencyArithmeticAndComparison() {
        Money usd = Money.of(1, USD);
        Money vnd = Money.of(1, VND);
        assertThatThrownBy(() -> usd.plus(vnd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usd.minus(vnd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usd.isGreaterThan(vnd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverflowInsteadOfWrapping() {
        Money max = Money.of(Long.MAX_VALUE, USD);
        assertThatThrownBy(() -> max.plus(Money.of(1, USD))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.of(Long.MIN_VALUE, USD).minus(Money.of(1, USD)))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.of(Long.MIN_VALUE, USD).negate())
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void comparesAmounts() {
        assertThat(Money.of(2, USD).isGreaterThan(Money.of(1, USD))).isTrue();
        assertThat(Money.of(1, USD).isLessThan(Money.of(2, USD))).isTrue();
    }
}
