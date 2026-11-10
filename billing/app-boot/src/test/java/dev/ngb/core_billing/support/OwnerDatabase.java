package dev.ngb.core_billing.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Direct access as the table owner (a superuser in the test container), for assertions that must see
 * every row regardless of row-level security. Kept out of the JdbcTemplate type on purpose: a second
 * JdbcTemplate bean would switch off Spring Boot's own.
 */
public final class OwnerDatabase {

    private final JdbcTemplate jdbc;

    OwnerDatabase(String url, String user, String password) {
        this.jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
