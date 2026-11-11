package dev.ngb.sampletenant.customer.adapter.persistence;

import dev.ngb.sampletenant.customer.application.port.CustomerRepository;
import dev.ngb.sampletenant.customer.domain.Customer;
import dev.ngb.sampletenant.customer.domain.CustomerStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcCustomerRepository implements CustomerRepository {

    private final CustomerJdbcRepository rows;
    private final JdbcTemplate jdbc;

    public JdbcCustomerRepository(CustomerJdbcRepository rows, JdbcTemplate jdbc) {
        this.rows = rows;
        this.jdbc = jdbc;
    }

    @Override
    public void save(Customer customer) {
        jdbc.update(
                """
                insert into customers
                    (id, user_id, billing_customer_id, display_name, email, status, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update
                    set billing_customer_id = excluded.billing_customer_id,
                        display_name = excluded.display_name,
                        email = excluded.email,
                        status = excluded.status
                """,
                customer.id(),
                customer.userId(),
                customer.billingCustomerId(),
                customer.displayName(),
                customer.email(),
                customer.status().name(),
                java.sql.Timestamp.from(customer.createdAt()));
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
