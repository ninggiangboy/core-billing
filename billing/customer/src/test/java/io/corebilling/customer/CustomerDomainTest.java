package io.corebilling.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.customer.domain.customer.Customer;
import io.corebilling.customer.domain.customer.CustomerCreated;
import io.corebilling.customer.domain.customer.ExternalIdentity;
import io.corebilling.platform.api.TenantSystemId;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    private static Customer customer() {
        return Customer.create(new CustomerId(UUID.randomUUID()), TENANT, "  Acme Ltd  ", "  billing@acme.test ", NOW);
    }

    @Test
    void createsActiveCustomerWithTrimmedFields() {
        Customer customer = customer();
        assertThat(customer.displayName()).isEqualTo("Acme Ltd");
        assertThat(customer.email()).isEqualTo("billing@acme.test");
        assertThat(customer.isActive()).isTrue();
        assertThat(customer.identities()).isEmpty();
    }

    @Test
    void emailIsOptionalButMustBeValidWhenGiven() {
        assertThat(Customer.create(new CustomerId(UUID.randomUUID()), TENANT, "No Mail", null, NOW).email()).isNull();
        assertThat(Customer.create(new CustomerId(UUID.randomUUID()), TENANT, "Blank Mail", "  ", NOW).email()).isNull();
        assertThatThrownBy(() -> Customer.create(new CustomerId(UUID.randomUUID()), TENANT, "Bad", "not-an-email", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void displayNameIsRequired() {
        assertThatThrownBy(() -> Customer.create(new CustomerId(UUID.randomUUID()), TENANT, " ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Customer.create(new CustomerId(UUID.randomUUID()), TENANT, "x".repeat(201), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mappingToTheSameExternalUserTwiceKeepsOneIdentity() {
        Customer customer = customer();
        ExternalIdentity identity = new ExternalIdentity(new TenantSystemId(UUID.randomUUID()), "user_001");
        customer.mapTo(identity);
        customer.mapTo(new ExternalIdentity(identity.tenantSystemId(), "user_001"));
        assertThat(customer.identities()).containsExactly(identity);
    }

    @Test
    void externalUserIdMustBePresentAndBounded() {
        TenantSystemId system = new TenantSystemId(UUID.randomUUID());
        assertThatThrownBy(() -> new ExternalIdentity(system, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalIdentity(system, "x".repeat(256))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDeactivatedCustomerCannotBeChangedButDeactivatingTwiceIsHarmless() {
        Customer customer = customer();
        customer.changeContact("New Name", null);
        assertThat(customer.displayName()).isEqualTo("New Name");
        assertThat(customer.email()).isNull();

        customer.deactivate();
        customer.deactivate();
        assertThat(customer.isActive()).isFalse();
        assertThatThrownBy(() -> customer.changeContact("Other", null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theCreatedEventCarriesNoPersonalData() {
        Customer customer = customer();
        customer.mapTo(new ExternalIdentity(new TenantSystemId(UUID.randomUUID()), "user_001"));
        CustomerCreated event = new CustomerCreated(UUID.randomUUID(), customer, NOW);

        assertThat(event.type()).isEqualTo("customer.created");
        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.aggregate()).isEqualTo("customer/" + customer.id());
        assertThat(event.payload().toString()).contains("user_001").doesNotContain("billing@acme.test");
    }
}
