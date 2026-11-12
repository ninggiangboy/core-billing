package dev.ngb.sampletenant.customer.application;

import dev.ngb.sampletenant.billing.api.client.BillingCustomers;
import dev.ngb.sampletenant.billing.api.model.Customer;
import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.customer.application.customer.query.CustomerReadRepository;
import dev.ngb.sampletenant.customer.application.customer.query.CustomerView;
import dev.ngb.sampletenant.customer.domain.customer.CustomerStatus;
import dev.ngb.sampletenant.shared.error.UpstreamUnavailableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class Fakes {

    private Fakes() {}

    static final class Customers implements CustomerRepository, CustomerReadRepository {
        private final Map<UUID, dev.ngb.sampletenant.customer.domain.customer.Customer> byId = new LinkedHashMap<>();

        @Override
        public void save(dev.ngb.sampletenant.customer.domain.customer.Customer customer) {
            byId.put(customer.id(), customer);
        }

        @Override
        public Optional<dev.ngb.sampletenant.customer.domain.customer.Customer> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<dev.ngb.sampletenant.customer.domain.customer.Customer> findByUserId(UUID userId) {
            return byId.values().stream().filter(customer -> customer.userId().equals(userId)).findFirst();
        }

        @Override
        public List<CustomerView> list() {
            return byId.values().stream().map(Customers::view).toList();
        }

        @Override
        public Optional<CustomerView> byId(UUID id) {
            return findById(id).map(Customers::view);
        }

        @Override
        public Optional<CustomerView> byUserId(UUID userId) {
            return findByUserId(userId).map(Customers::view);
        }

        private static CustomerView view(dev.ngb.sampletenant.customer.domain.customer.Customer customer) {
            return new CustomerView(
                    customer.id(), customer.userId(), customer.billingCustomerId(), customer.displayName(),
                    customer.email(), customer.status(), customer.createdAt());
        }
    }

    static final class Billing implements BillingCustomers {
        private final List<NewCustomer> created = new ArrayList<>();
        private boolean unavailable;
        private boolean conflict;
        private String existingId = "cus_existing";

        void unavailable() {
            unavailable = true;
        }

        void conflict() {
            conflict = true;
        }

        void recover() {
            unavailable = false;
        }

        List<NewCustomer> created() {
            return created;
        }

        @Override
        public Customer create(String idempotencyKey, NewCustomer customer) {
            if (unavailable) {
                throw new UpstreamUnavailableException("billing is down");
            }
            if (conflict) {
                throw new dev.ngb.sampletenant.shared.error.ConflictException("already mapped");
            }
            created.add(customer);
            return new Customer("cus_" + created.size(), customer.displayName(), customer.email(), "ACTIVE", null);
        }

        @Override
        public Customer get(String id) {
            return new Customer(id, "Ada", "ada@example.com", "ACTIVE", Instant.parse("2026-11-11T00:00:00Z"));
        }

        @Override
        public Customer byExternalUser(String externalUserId, String tenantSystemId) {
            return new Customer(existingId, "Ada", "ada@example.com", "ACTIVE", null);
        }
    }

    static boolean mapped(dev.ngb.sampletenant.customer.domain.customer.Customer customer) {
        return customer.status() == CustomerStatus.MAPPED;
    }
}
