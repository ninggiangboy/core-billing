package dev.ngb.sampletenant.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.customer.application.customer.command.RegisterCustomerUseCase;
import dev.ngb.sampletenant.customer.application.customer.service.CustomerMapping;
import dev.ngb.sampletenant.customer.domain.customer.Customer;
import dev.ngb.sampletenant.customer.domain.customer.CustomerStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterCustomerUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    private final Fakes.Customers customers = new Fakes.Customers();
    private final Fakes.Billing billing = new Fakes.Billing();
    private final RegisterCustomerUseCase register = new RegisterCustomerUseCase(
            customers,
            new CustomerMapping(customers, billing, "sys_1"),
            () -> NOW,
            () -> UUID.randomUUID());

    @Test
    void registerMapsAndStoresTheBillingCustomer() {
        UUID userId = UUID.randomUUID();

        Customer customer = register.register(userId, "Ada", "ada@example.com");

        assertThat(customer.status()).isEqualTo(CustomerStatus.MAPPED);
        assertThat(customer.billingCustomerId()).isEqualTo("cus_1");
        assertThat(billing.created()).singleElement().satisfies(created -> {
            assertThat(created.tenantSystemId()).isEqualTo("sys_1");
            assertThat(created.externalUserId()).isEqualTo(userId.toString());
        });
    }

    @Test
    void registerLeavesTheCustomerPendingWhenBillingIsUnreachable() {
        billing.unavailable();

        Customer customer = register.register(UUID.randomUUID(), "Ada", "ada@example.com");

        assertThat(customer.status()).isEqualTo(CustomerStatus.PENDING);
        assertThat(customer.billingCustomerId()).isNull();
    }

    @Test
    void retryMapsAPendingCustomer() {
        billing.unavailable();
        Customer pending = register.register(UUID.randomUUID(), "Ada", "ada@example.com");
        billing.recover();

        Customer mapped = register.retry(pending.id());

        assertThat(mapped.status()).isEqualTo(CustomerStatus.MAPPED);
        assertThat(mapped.billingCustomerId()).isEqualTo("cus_1");
    }

    @Test
    void conflictRecoversTheExistingMapping() {
        billing.conflict();

        Customer customer = register.register(UUID.randomUUID(), "Ada", "ada@example.com");

        assertThat(customer.status()).isEqualTo(CustomerStatus.MAPPED);
        assertThat(customer.billingCustomerId()).isEqualTo("cus_existing");
    }

    @Test
    void registerIsIdempotentForTheSameAccount() {
        UUID userId = UUID.randomUUID();

        Customer first = register.register(userId, "Ada", "ada@example.com");
        Customer second = register.register(userId, "Ada", "ada@example.com");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(billing.created()).hasSize(1);
    }
}
