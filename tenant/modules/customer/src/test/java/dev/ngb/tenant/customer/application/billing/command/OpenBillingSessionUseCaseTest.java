package dev.ngb.tenant.customer.application.billing.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.tenant.billing.api.model.BillingSession;
import dev.ngb.tenant.customer.application.billing.BillingFakes;
import dev.ngb.tenant.shared.error.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenBillingSessionUseCaseTest {

    private final BillingFakes.Sessions sessions = new BillingFakes.Sessions();

    @Test
    void opensASessionForTheMappedCustomerWithTheDefaultReturnUrl() {
        OpenBillingSessionUseCase open = new OpenBillingSessionUseCase(
                BillingFakes.mappedCustomer(), sessions, "http://localhost:5174/portal/return");

        BillingSession session = open.execute(BillingFakes.USER, null, List.of("wallet:view"), 3600L);

        assertThat(session.portalUrl()).isEqualTo("https://tenant-a.example/portal#token");
        assertThat(sessions.customerId).isEqualTo("cus_1");
        assertThat(sessions.returnUrl).isEqualTo("http://localhost:5174/portal/return");
    }

    @Test
    void refusesAnUnmappedCustomer() {
        OpenBillingSessionUseCase open = new OpenBillingSessionUseCase(
                BillingFakes.unmappedCustomer(), sessions, "http://localhost:5174/portal/return");

        assertThatThrownBy(() -> open.execute(BillingFakes.USER, null, List.of(), null))
                .isInstanceOf(BadRequestException.class);
    }
}
