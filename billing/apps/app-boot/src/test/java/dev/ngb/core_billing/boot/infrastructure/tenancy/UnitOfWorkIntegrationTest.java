package dev.ngb.core_billing.boot.infrastructure.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.platform.api.idempotency.IdempotencyStore;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.idempotency.StoredResponse;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.sharedkernel.event.EventPublisher;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.TestEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class UnitOfWorkIntegrationTest {

    @Autowired UnitOfWork unitOfWork;
    @Autowired EventPublisher events;
    @Autowired IdempotencyStore idempotency;
    @Autowired AuditLog auditLog;
    @Autowired TenantContext tenantContext;
    @Autowired OwnerDatabase owner;
    @Autowired ScopeTestSupport scopes;

    private long rows(String table, TenantId tenantId) {
        return owner.count("select count(*) from " + table + " where tenant_id = ?", tenantId.value());
    }

    private void writeEverything(TenantId tenantId) {
        events.publish(TestEvent.forTenant(tenantId));
        idempotency.begin(tenantId, "charge", "key-1", "hash-1");
        auditLog.record(AuditEntry.tenant(tenantId, "tester", "thing.done", "thing/1", "req-1", Instant.now()));
    }

    @Test
    void outboxIdempotencyAndAuditCommitTogether() {
        Tenant tenant = scopes.newTenant();

        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> writeEverything(tenant.id())));

        assertThat(rows("outbox", tenant.id())).isEqualTo(1);
        assertThat(rows("idempotency_keys", tenant.id())).isEqualTo(1);
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
        assertThat(rows("idempotency_keys", tenant.id())).isZero();
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

    @Test
    void aRepeatedKeyReturnsTheStoredResponse() {
        Tenant tenant = scopes.newTenant();
        StoredResponse response = new StoredResponse(201, "{\"id\":\"abc\"}");

        Optional<StoredResponse> first = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> {
            Optional<StoredResponse> seen = idempotency.begin(tenant.id(), "charge", "k", "h");
            idempotency.complete(tenant.id(), "charge", "k", response);
            return seen;
        }));
        Optional<StoredResponse> second = scopes.inTenant(tenant.id(),
                () -> unitOfWork.transactional(() -> idempotency.begin(tenant.id(), "charge", "k", "h")));

        assertThat(first).isEmpty();
        assertThat(second).contains(response);
        assertThat(rows("idempotency_keys", tenant.id())).isEqualTo(1);
    }

    @Test
    void aChangedPayloadUnderTheSameKeyIsAConflict() {
        Tenant tenant = scopes.newTenant();
        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> {
            idempotency.begin(tenant.id(), "charge", "k", "h1");
            idempotency.complete(tenant.id(), "charge", "k", new StoredResponse(200, "{}"));
        }));

        assertThatThrownBy(() -> scopes.inTenant(tenant.id(),
                        () -> unitOfWork.transactional(() -> idempotency.begin(tenant.id(), "charge", "k", "h2"))))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void sameKeyInAnotherTenantIsIndependent() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        Optional<StoredResponse> inA = scopes.inTenant(a.id(), () -> unitOfWork.transactional(() -> idempotency.begin(a.id(), "s", "k", "h")));
        Optional<StoredResponse> inB = scopes.inTenant(b.id(), () -> unitOfWork.transactional(() -> idempotency.begin(b.id(), "s", "k", "other")));
        assertThat(inA).isEmpty();
        assertThat(inB).isEmpty();
    }

    @Test
    void concurrentDuplicatesWaitForTheFirstAndSeeItsResponse() throws Exception {
        Tenant tenant = scopes.newTenant();
        StoredResponse response = new StoredResponse(200, "{\"charged\":true}");
        CountDownLatch firstHasKey = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Optional<StoredResponse>> first = CompletableFuture.supplyAsync(
                    () -> scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> {
                        Optional<StoredResponse> seen = idempotency.begin(tenant.id(), "charge", "dup", "h");
                        firstHasKey.countDown();
                        pause(400);
                        idempotency.complete(tenant.id(), "charge", "dup", response);
                        return seen;
                    })),
                    pool);
            assertThat(firstHasKey.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Optional<StoredResponse>> second = CompletableFuture.supplyAsync(
                    () -> scopes.inTenant(tenant.id(),
                            () -> unitOfWork.transactional(() -> idempotency.begin(tenant.id(), "charge", "dup", "h"))),
                    pool);

            assertThat(first.get(15, TimeUnit.SECONDS)).isEmpty();
            assertThat(second.get(15, TimeUnit.SECONDS)).contains(response);
            assertThat(rows("idempotency_keys", tenant.id())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
