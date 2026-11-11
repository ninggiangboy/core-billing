package dev.ngb.core_billing.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.customer.adapter.customer.persistence.JooqCustomerRepository;
import dev.ngb.core_billing.customer.application.customer.command.CreateCustomerUseCase;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
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
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A key recorded by the generic store before the migration must keep replaying the same customer afterwards, and a
 * customer the store never recorded still gets a key, so the columns can be required.
 */
class CustomerIdempotencyMigrationTest {

    private static final String APP_PASSWORD = "app-role-test-secret";
    private static final String KEY = "original-key";
    /**
     * The hash the store recorded for "Acme" with no tenant system, external user or email, computed the way the
     * create use case has always computed it. If the use case ever hashes differently, the replay below conflicts.
     */
    private static final String HASH = Fingerprint.sha256(String.join("\u0000", "null", "null", "Acme", ""));

    @Test
    void storedKeysKeepReplayingThroughTheUseCaseAndEveryOtherCustomerGetsAKey() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(PostgresContainerConfig.IMAGE)) {
            postgres.start();
            UUID tenantId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            UUID unrecordedId = UUID.randomUUID();
            try (Connection owner = connect(postgres)) {
                migrate(postgres, "17");
                seed(owner, tenantId, customerId, unrecordedId);
                migrate(postgres, null);

                try (Statement statement = owner.createStatement()) {
                    statement.execute("select set_config('app.tenant_id', '" + tenantId + "', false)");
                }
                assertThat(keyAndHash(owner, customerId)).containsExactly(KEY, HASH);
                assertThat(keyAndHash(owner, unrecordedId)).as("a customer the store never recorded")
                        .containsExactly("legacy:" + unrecordedId, "legacy");

                CreateCustomerUseCase.Result replay = createCustomer(owner, new TenantId(tenantId)).execute(
                        new CreateCustomerUseCase.Command(null, null, "Acme", null, KEY, new Audit("user/test", "req-1")));
                assertThat(replay.replayed()).isTrue();
                assertThat(replay.customer().id().value()).isEqualTo(customerId);

                try (Statement statement = owner.createStatement();
                     ResultSet row = statement.executeQuery("select to_regclass('public.idempotency_keys')")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getObject(1)).as("the generic store is gone").isNull();
                }
            }
        }
    }

    /** The real use case and repository over the migrated database; a replay must neither publish nor audit. */
    private static CreateCustomerUseCase createCustomer(Connection owner, TenantId tenant) {
        UnitOfWork direct = new UnitOfWork() {
            @Override
            public <T> T transactional(Supplier<T> work) {
                return work.get();
            }
        };
        TenantContext inTenant = new TenantContext() {
            @Override
            public RequestScope current() {
                return RequestScope.tenant(tenant);
            }

            @Override
            public <T> T callAs(RequestScope scope, Supplier<T> work) {
                return work.get();
            }
        };
        return new CreateCustomerUseCase(
                direct,
                new JooqCustomerRepository(DSL.using(owner, SQLDialect.POSTGRES)),
                event -> {
                    throw new AssertionError("a replay publishes no event");
                },
                entry -> {
                    throw new AssertionError("a replay writes no audit entry");
                },
                inTenant,
                UUID::randomUUID,
                Instant::now);
    }

    private static String[] keyAndHash(Connection owner, UUID customerId) throws Exception {
        try (PreparedStatement customer = owner.prepareStatement("select idempotency_key, request_hash from customers where id = ?")) {
            customer.setObject(1, customerId);
            try (ResultSet row = customer.executeQuery()) {
                assertThat(row.next()).isTrue();
                return new String[] {row.getString("idempotency_key"), row.getString("request_hash")};
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

    private static void seed(Connection owner, UUID tenantId, UUID customerId, UUID unrecordedId) throws Exception {
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
        for (UUID id : new UUID[] {customerId, unrecordedId}) {
            try (PreparedStatement customer = owner.prepareStatement(
                    "insert into customers (id, tenant_id, display_name, email, status, created_at) values (?, ?, 'Acme', null, 'ACTIVE', ?)")) {
                customer.setObject(1, id);
                customer.setObject(2, tenantId);
                customer.setTimestamp(3, Timestamp.from(Instant.now()));
                customer.executeUpdate();
            }
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
