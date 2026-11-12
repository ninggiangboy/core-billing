package dev.ngb.billing.boot.infrastructure.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.event.EventPublisher;
import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.Application;
import dev.ngb.billing.platform.api.actor.RequestScope;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.platform.api.tenant.TenantContext;
import dev.ngb.billing.platform.domain.tenant.Tenant;
import dev.ngb.billing.support.OwnerDatabase;
import dev.ngb.billing.support.PostgresContainerConfig;
import dev.ngb.billing.support.ScopeTestSupport;
import dev.ngb.billing.support.TestEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class UnitOfWorkIntegrationTest {

    @Autowired UnitOfWork unitOfWork;
    @Autowired EventPublisher events;
    @Autowired AuditLog auditLog;
    @Autowired TenantContext tenantContext;
    @Autowired OwnerDatabase owner;
    @Autowired ScopeTestSupport scopes;

    private long rows(String table, TenantId tenantId) {
        return owner.count("select count(*) from " + table + " where tenant_id = ?", tenantId.value());
    }

    private void writeEverything(TenantId tenantId) {
        events.publish(TestEvent.forTenant(tenantId));
        auditLog.record(AuditEntry.tenant(tenantId, "tester", "thing.done", "thing/1", "req-1", Instant.now()));
    }

    @Test
    void outboxAndAuditCommitTogether() {
        Tenant tenant = scopes.newTenant();

        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> writeEverything(tenant.id())));

        assertThat(rows("outbox", tenant.id())).isEqualTo(1);
        assertThat(rows("audit_log", tenant.id())).isEqualTo(1);
    }

    @Test
    void aFailureAfterTheOutboxAppendRollsBackEveryTable() {
        Tenant tenant = scopes.newTenant();

        assertThatThrownBy(() -> scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> {
                    writeEverything(tenant.id());
                    throw new IllegalStateException("injected failure after the outbox append");
                })))
                .hasMessageContaining("injected failure");

        assertThat(rows("outbox", tenant.id())).isZero();
        assertThat(rows("audit_log", tenant.id())).isZero();
    }

    @Test
    void publishingOutsideAUnitOfWorkIsRejected() {
        Tenant tenant = scopes.newTenant();
        assertThatThrownBy(() -> scopes.inTenant(tenant.id(), () -> {
                    events.publish(TestEvent.forTenant(tenant.id()));
                    return null;
                }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void usingTheUnitOfWorkWithoutAScopeIsRejected() {
        assertThatThrownBy(() -> unitOfWork.transactional(() -> "never"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no request scope");
    }

    @Test
    void aNestedUnitOfWorkJoinsInTheSameScopeAndRejectsADifferentOne() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();

        scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> unitOfWork.transactional(() -> events.publish(TestEvent.forTenant(a.id())))));
        assertThat(rows("outbox", a.id())).isEqualTo(1);

        assertThatThrownBy(() -> scopes.inTenant(a.id(), () -> unitOfWork.transactional(
                        () -> tenantContext.callAs(RequestScope.tenant(b.id()), () -> unitOfWork.transactional(() -> "nested")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same scope");
    }
}
