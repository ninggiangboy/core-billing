package dev.ngb.sampletenant.customer.application.billing.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.customer.application.billing.BillingFakes;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;

class TopUpUseCaseTest {

    private final BillingFakes.Payments payments = new BillingFakes.Payments();
    private final TopUpUseCase topUp =
            new TopUpUseCase(BillingFakes.mappedCustomer(), new BillingFakes.Wallets(), payments);

    @Test
    void refusesATopUpForAnotherCustomersWallet() {
        assertThatThrownBy(() -> topUp.execute(BillingFakes.USER, "wal_other", 500, "USD", "key"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void startsATopUpForAnOwnedWallet() {
        Payment payment = topUp.execute(BillingFakes.USER, "wal_1", 500, "USD", "key-1");

        assertThat(payment.id()).isEqualTo("pay_1");
        assertThat(payments.topUpKey).isEqualTo("key-1");
    }
}
