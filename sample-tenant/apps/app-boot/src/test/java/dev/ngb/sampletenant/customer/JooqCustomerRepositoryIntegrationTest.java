package dev.ngb.sampletenant.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.customer.application.customer.query.CustomerReadRepository;
import dev.ngb.sampletenant.customer.domain.customer.Customer;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.user.command.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqCustomerRepositoryIntegrationTest extends HttpIntegrationTest {

    @Autowired
    CustomerRepository customers;

    @Autowired
    CustomerReadRepository read;

    @Autowired
    IdGenerator ids;

    /** Registration creates a PENDING customer when Billing is unavailable; use that row. */
    private Customer existingCustomer() {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        billing.customersUnavailable();
        try {
            User user = registerUser.execute(
                    new RegisterUserUseCase.Command(email, "Customer", HttpWorld.PASSWORD, Role.VIEWER));
            return customers.findByUserId(user.id()).orElseThrow();
        } finally {
            billing.customersRecover();
        }
    }

    @Test
    void roundTripsAndUpsertsTheMapping() {
        Customer customer = existingCustomer();

        assertThat(customer.mapped()).isFalse();

        customers.save(customer.mapped("cus_9"));

        assertThat(customers.findById(customer.id()).orElseThrow().billingCustomerId()).isEqualTo("cus_9");
        assertThat(customers.findById(customer.id()).orElseThrow().mapped()).isTrue();
    }

    @Test
    void theReadSideSelectsIntoViews() {
        Customer customer = existingCustomer().mapped("cus_10");
        customers.save(customer);

        assertThat(read.byId(customer.id()).orElseThrow().mapped()).isTrue();
        assertThat(read.byUserId(customer.userId()).orElseThrow().billingCustomerId()).isEqualTo("cus_10");
        assertThat(read.list()).extracting(view -> view.id()).contains(customer.id());
    }
}
