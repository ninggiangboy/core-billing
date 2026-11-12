package dev.ngb.sampletenant.customer.application.billing.query;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.billing.api.model.Invoice;
import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.customer.application.billing.BillingFakes;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerBillingQueriesTest {

    private final BillingFakes.Invoices invoices = new BillingFakes.Invoices();
    private final BillingFakes.Payments payments = new BillingFakes.Payments();
    private final CustomerBillingQueries queries = new CustomerBillingQueries(
            BillingFakes.mappedCustomer(), invoices, payments, new BillingFakes.Wallets());

    @Test
    void invoicesAreScopedToTheMappedCustomer() {
        List<Invoice> result = queries.invoices(BillingFakes.USER);

        assertThat(result).hasSize(1);
        assertThat(invoices.customerId).isEqualTo("cus_1");
    }

    @Test
    void paymentsAggregateTheWalletHistory() {
        List<Payment> result = queries.payments(BillingFakes.USER);

        assertThat(result).hasSize(2);
    }
}
