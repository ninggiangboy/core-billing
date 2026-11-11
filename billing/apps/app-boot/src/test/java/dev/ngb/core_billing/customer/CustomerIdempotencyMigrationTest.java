package dev.ngb.core_billing.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A key recorded by the generic store before the migration must keep replaying the same customer afterwards.
 */
class CustomerIdempotencyMigrationTest {

    private static final String APP_PASSWORD = "app-role-test-secret";
    private static final String KEY = "original-key";
    /** The request the store recorded: no tenant system, no external user, "Acme", no email. */
    private static final String HASH = Fingerprint.sha256(String.join("\u0000", "null", "null", "Acme", ""));

    @Test
    void anExistingKeyIsBackfilledOntoTheCustomerAndStillReplays() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(PostgresContainerConfig.IMAGE)) {
            postgres.start();
            UUID tenantId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            try (Connection owner = connect(postgres)) {
                migrate(postgres, "17");
                seed(owner, tenantId, customerId);
                migrate(postgres, null);

                try (Statement statement = owner.createStatement()) {
                    statement.execute("select set_config('app.tenant_id', '" + tenantId + "', false)");
                }
                try (PreparedStatement customer = owner.prepareStatement(
                        "select idempotency_key, request_hash from customers where id = ?")) {
                    customer.setObject(1, customerId);
                    try (ResultSet row = customer.executeQuery()) {
                        assertThat(row.next()).isTrue();
                        assertThat(row.getString("idempotency_key")).isEqualTo(KEY);
                        assertThat(row.getString("request_hash")).isEqualTo(HASH);
                    }
                }
                try (PreparedStatement replay = owner.prepareStatement(
                        "select id from customers where tenant_id = ? and idempotency_key = ?")) {
                    replay.setObject(1, tenantId);
                    replay.setString(2, KEY);
                    try (ResultSet row = replay.executeQuery()) {
                        assertThat(row.next()).isTrue();
                        assertThat(row.getObject("id", UUID.class)).isEqualTo(customerId);
                    }
                }
                try (Statement statement = owner.createStatement();
                     ResultSet row = statement.executeQuery("select to_regclass('public.idempotency_keys')")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getObject(1)).as("the generic store is gone").isNull();
                }
            }
        }
    }

    private static Connection connect(PostgreSQLContainer postgres) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void migrate(PostgreSQLContainer postgres, String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("appDbPassword", APP_PASSWORD));
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        configuration.load().migrate();
    }

    private static void seed(Connection owner, UUID tenantId, UUID customerId) throws Exception {
        owner.setAutoCommit(false);
        try (Statement statement = owner.createStatement()) {
            statement.execute("select set_config('app.tenant_id', '" + tenantId + "', true)");
        }
        String code = "t" + tenantId.toString().replace("-", "").substring(0, 12);
        try (PreparedStatement tenant = owner.prepareStatement(
                "insert into tenants (id, code, name, status, created_at) values (?, ?, ?, 'ACTIVE', ?)")) {
            tenant.setObject(1, tenantId);
            tenant.setString(2, code);
            tenant.setString(3, "Tenant " + code);
            tenant.setTimestamp(4, Timestamp.from(Instant.now()));
            tenant.executeUpdate();
        }
        try (PreparedStatement customer = owner.prepareStatement(
                "insert into customers (id, tenant_id, display_name, email, status, created_at) values (?, ?, 'Acme', null, 'ACTIVE', ?)")) {
            customer.setObject(1, customerId);
            customer.setObject(2, tenantId);
            customer.setTimestamp(3, Timestamp.from(Instant.now()));
            customer.executeUpdate();
        }
        try (PreparedStatement stored = owner.prepareStatement(
                "insert into idempotency_keys (tenant_id, scope, key, request_hash, response_status, response_body) "
                        + "values (?, 'customer.create', ?, ?, 201, ?)")) {
            stored.setObject(1, tenantId);
            stored.setString(2, KEY);
            stored.setString(3, HASH);
            stored.setString(4, customerId.toString());
            stored.executeUpdate();
        }
        owner.commit();
    }
}
