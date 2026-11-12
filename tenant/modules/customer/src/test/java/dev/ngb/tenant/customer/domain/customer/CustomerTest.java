package dev.ngb.tenant.customer.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final IdGenerator IDS = () -> ID;

    @Test
    void aNewCustomerIsPendingWithoutABillingId() {
        Customer customer = Customer.pending(IDS, USER_ID, "Ada", "ada@example.com", NOW);

        assertThat(customer.id()).isEqualTo(ID);
        assertThat(customer.userId()).isEqualTo(USER_ID);
        assertThat(customer.billingCustomerId()).isNull();
        assertThat(customer.status()).isEqualTo(CustomerStatus.PENDING);
        assertThat(customer.createdAt()).isEqualTo(NOW);
        assertThat(customer.mapped()).isFalse();
    }

    @Test
    void mappingStoresTheBillingIdAndKeepsTheRest() {
        Customer pending = Customer.pending(IDS, USER_ID, "Ada", "ada@example.com", NOW);

        Customer mapped = pending.mapped("cus_1");

        assertThat(mapped.status()).isEqualTo(CustomerStatus.MAPPED);
        assertThat(mapped.billingCustomerId()).isEqualTo("cus_1");
        assertThat(mapped.mapped()).isTrue();
        assertThat(mapped).usingRecursiveComparison().ignoringFields("status", "billingCustomerId").isEqualTo(pending);
    }

    @Test
    void aMappingNeedsBothTheMappedStatusAndABillingId() {
        Customer mappedWithoutId = new Customer(ID, USER_ID, null, "Ada", "ada@example.com", CustomerStatus.MAPPED, NOW);
        Customer idWithoutStatus = new Customer(ID, USER_ID, "cus_1", "Ada", "ada@example.com", CustomerStatus.PENDING, NOW);

        assertThat(mappedWithoutId.mapped()).isFalse();
        assertThat(idWithoutStatus.mapped()).isFalse();
    }

    @Test
    void disablingKeepsTheBillingIdButEndsTheMapping() {
        Customer disabled = Customer.pending(IDS, USER_ID, "Ada", "ada@example.com", NOW).mapped("cus_1").disabled();

        assertThat(disabled.status()).isEqualTo(CustomerStatus.DISABLED);
        assertThat(disabled.billingCustomerId()).isEqualTo("cus_1");
        assertThat(disabled.mapped()).isFalse();
    }
}
