package dev.ngb.sampletenant.customer.adapter.persistence;

import dev.ngb.sampletenant.customer.application.port.CustomerRepository;
import dev.ngb.sampletenant.customer.domain.Customer;
import dev.ngb.sampletenant.customer.domain.CustomerStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcCustomerRepository implements CustomerRepository {

    private final CustomerJdbcRepository rows;

    public JdbcCustomerRepository(CustomerJdbcRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(Customer customer) {
        rows.save(new CustomerRow(
                customer.id(),
                customer.userId(),
                customer.billingCustomerId(),
                customer.displayName(),
                customer.email(),
                customer.status().name(),
                customer.createdAt()));
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return rows.findById(id).map(JdbcCustomerRepository::toDomain);
    }

    @Override
    public Optional<Customer> findByUserId(UUID userId) {
        return rows.findByUserId(userId).map(JdbcCustomerRepository::toDomain);
    }

    @Override
    public List<Customer> findAll() {
        return rows.findAllByOrderByCreatedAtAsc().stream().map(JdbcCustomerRepository::toDomain).toList();
    }

    private static Customer toDomain(CustomerRow row) {
        return new Customer(
                row.id(),
                row.userId(),
                row.billingCustomerId(),
                row.displayName(),
                row.email(),
                CustomerStatus.valueOf(row.status()),
                row.createdAt());
    }
}
