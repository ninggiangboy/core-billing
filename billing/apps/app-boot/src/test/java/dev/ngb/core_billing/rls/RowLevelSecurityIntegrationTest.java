package dev.ngb.core_billing.rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.TestEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** One connection in the pool, so a tenant setting that leaked would be seen by the next transaction. */
@SpringBootTest(classes = Application.class, properties = "spring.datasource.hikari.maximum-pool-size=1")
@Import(PostgresContainerConfig.class)
class RowLevelSecurityIntegrationTest {

    @Autowired UnitOfWork unitOfWork;
    @Autowired EventPublisher events;
    @Autowired JdbcTemplate appJdbc;
    @Autowired OwnerDatabase owner;
    @Autowired ScopeTestSupport scopes;

    private List<UUID> outboxIds() {
        return appJdbc.queryForList("select id from outbox", UUID.class);
    }

    @Test
    void noTenantSeesNothingAndEachTenantSeesOnlyItself() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        TestEvent eventA = TestEvent.forTenant(a.id());
        TestEvent eventB = TestEvent.forTenant(b.id());
        scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> events.publish(eventA)));
        scopes.runInTenant(b.id(), () -> unitOfWork.transactional(() -> events.publish(eventB)));

        // Platform scope sets no tenant: tenant-scoped rows are invisible.
        assertThat(scopes.onPlatform(() -> unitOfWork.transactional(this::outboxIds))).isEmpty();
        // Alternating tenants on the single connection never shows the other tenant's row.
        for (int i = 0; i < 3; i++) {
            assertThat(scopes.inTenant(a.id(), () -> unitOfWork.transactional(this::outboxIds)))
                    .containsExactly(eventA.eventId());
            assertThat(scopes.inTenant(b.id(), () -> unitOfWork.transactional(this::outboxIds)))
                    .containsExactly(eventB.eventId());
        }
        // The same connection, outside any transaction and tenant, sees nothing: the setting did not leak.
        assertThat(outboxIds()).isEmpty();
        // The owner connection confirms both rows really exist.
        assertThat(owner.count("select count(*) from outbox where id in (?, ?)", eventA.eventId(), eventB.eventId()))
                .isEqualTo(2);
    }

    @Test
    void aTenantCannotWriteRowsForAnotherTenant() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        TestEvent forB = TestEvent.forTenant(b.id());

        assertThatThrownBy(() -> scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> events.publish(forB))))
                .isInstanceOf(DataAccessException.class);
        assertThat(owner.count("select count(*) from outbox where id = ?", forB.eventId())).isZero();
    }

    @Test
    void aWriteOutsideAnyUnitOfWorkFailsClosed() {
        Tenant a = scopes.newTenant();
        TestEvent event = TestEvent.forTenant(a.id());
        assertThatThrownBy(() -> appJdbc.update(
                        "insert into outbox (id, tenant_id, aggregate, type, payload, created_at, next_attempt_at)"
                                + " values (?, ?, 'x', 'x', '{}'::jsonb, now(), now())",
                        event.eventId(),
                        a.id().value()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void compositeForeignKeysRejectCrossTenantReferences() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        String parent = "fk_parent_" + System.nanoTime();
        String child = "fk_child_" + System.nanoTime();
        owner.jdbc().execute("create table " + parent + " (tenant_id uuid not null, id uuid not null, primary key (tenant_id, id))");
        owner.jdbc().execute("create table " + child + " (tenant_id uuid not null, id uuid primary key, parent_id uuid not null,"
                + " foreign key (tenant_id, parent_id) references " + parent + " (tenant_id, id))");
        try {
            UUID parentOfB = UUID.randomUUID();
            owner.jdbc().update("insert into " + parent + " values (?, ?)", b.id().value(), parentOfB);

            assertThatThrownBy(() -> owner.jdbc().update(
                            "insert into " + child + " values (?, ?, ?)", a.id().value(), UUID.randomUUID(), parentOfB))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("foreign key");
            assertThat(owner.jdbc().update("insert into " + child + " values (?, ?, ?)", b.id().value(), UUID.randomUUID(), parentOfB))
                    .isEqualTo(1);
        } finally {
            owner.jdbc().execute("drop table " + child);
            owner.jdbc().execute("drop table " + parent);
        }
    }
}
