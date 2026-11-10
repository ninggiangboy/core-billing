package dev.ngb.core_billing.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.customer.application.command.AuditContext;
import dev.ngb.core_billing.customer.application.command.CreateCustomerUseCase;
import dev.ngb.core_billing.customer.application.command.CustomerAdministration;
import dev.ngb.core_billing.customer.application.query.CustomerQueries;
import dev.ngb.core_billing.customer.application.port.CustomerRepository;
import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.customer.domain.customer.ExternalIdentity;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.application.auth.Caller;
import dev.ngb.core_billing.platform.application.tenantsystem.command.TenantSystemAdministration;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class CustomerIntegrationTest {

    private static final AuditContext AUDIT = new AuditContext("user/tester", "req-1");

    @Autowired UnitOfWork unitOfWork;
    @Autowired CustomerRepository customers;
    @Autowired CreateCustomerUseCase createCustomer;
    @Autowired CustomerAdministration administration;
    @Autowired CustomerQueries customerQueries;
    @Autowired CustomerLookup lookup;
    @Autowired TenantSystemAdministration systems;
    @Autowired Clock clock;
    @Autowired IdGenerator ids;
    @Autowired OwnerDatabase owner;
    @Autowired ScopeTestSupport scopes;

    private TenantSystemId newSystem(Tenant tenant) {
        return scopes.inTenant(tenant.id(), () -> systems.create(new Caller("user/tester", "req"), "Sample " + UUID.randomUUID())).id();
    }

    private CreateCustomerUseCase.Command command(TenantSystemId system, String externalUserId, String name, String key) {
        return new CreateCustomerUseCase.Command(system, externalUserId, name, null, key, AUDIT);
    }

    @Test
    void aCustomerRoundTripsWithItsMappingAndUpdatesPersist() {
        Tenant tenant = scopes.newTenant();
        TenantSystemId system = newSystem(tenant);
        Customer created = scopes.inTenant(tenant.id(),
                () -> createCustomer.execute(new CreateCustomerUseCase.Command(system, "user_001", "Acme", "billing@acme.test", "key-1", AUDIT))).customer();

        Customer reloaded = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> customers.findById(created.id()))).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(created);
        assertThat(reloaded.identities()).containsExactly(new ExternalIdentity(system, "user_001"));

        Customer updated = scopes.inTenant(tenant.id(), () -> administration.update(AUDIT, created.id(), "Acme Ltd", null));
        Customer afterUpdate = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> customers.findById(created.id()))).orElseThrow();
        assertThat(afterUpdate).usingRecursiveComparison().isEqualTo(updated);
        assertThat(afterUpdate.identities()).as("the mapping survives an update").hasSize(1);

        scopes.runInTenant(tenant.id(), () -> administration.deactivate(AUDIT, created.id()));
        assertThat(scopes.inTenant(tenant.id(), () -> customerQueries.get(created.id())).isActive()).isFalse();
        assertThatThrownBy(() -> scopes.runInTenant(tenant.id(), () -> administration.update(AUDIT, created.id(), "Again", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theSameExternalUserIdInTwoTenantsIsTwoIndependentCustomers() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        TenantSystemId systemA = newSystem(a);
        TenantSystemId systemB = newSystem(b);

        Customer inA = scopes.inTenant(a.id(), () -> createCustomer.execute(command(systemA, "user_001", "A's customer", "k1"))).customer();
        Customer inB = scopes.inTenant(b.id(), () -> createCustomer.execute(command(systemB, "user_001", "B's customer", "k1"))).customer();

        assertThat(inA.id()).isNotEqualTo(inB.id());
        assertThat(scopes.inTenant(a.id(), () -> customerQueries.getByExternalUser(systemA, "user_001")).id()).isEqualTo(inA.id());
        assertThat(scopes.inTenant(b.id(), () -> customerQueries.getByExternalUser(systemB, "user_001")).id()).isEqualTo(inB.id());
        assertThatThrownBy(() -> scopes.inTenant(b.id(), () -> customerQueries.get(inA.id()))).isInstanceOf(NotFoundException.class);
        assertThat(scopes.inTenant(b.id(), () -> lookup.find(inA.id()))).as("another tenant's customer is invisible to the lookup").isEmpty();
        assertThat(scopes.inTenant(a.id(), () -> lookup.find(inA.id())).orElseThrow().displayName()).isEqualTo("A's customer");
    }

    @Test
    void retryingWithTheSameKeyReturnsTheSameCustomerAndWritesOnceEverywhere() {
        Tenant tenant = scopes.newTenant();
        TenantSystemId system = newSystem(tenant);

        var first = scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(system, "user_001", "Acme", "retry-key")));
        var second = scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(system, "user_001", "Acme", "retry-key")));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.customer().id()).isEqualTo(first.customer().id());
        assertThat(owner.count("select count(*) from customers where tenant_id = ?", tenant.id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from customer_mappings where tenant_id = ?", tenant.id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'customer.created'", tenant.id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and action = 'customer.created'", tenant.id().value())).isEqualTo(1);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        Tenant tenant = scopes.newTenant();
        TenantSystemId system = newSystem(tenant);
        scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(system, "user_001", "Acme", "shared-key")));

        assertThatThrownBy(() -> scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(system, "user_002", "Other", "shared-key"))))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(owner.count("select count(*) from customers where tenant_id = ?", tenant.id().value())).isEqualTo(1);
    }

    @Test
    void anExternalUserCannotBeMappedTwiceUnderDifferentKeys() {
        Tenant tenant = scopes.newTenant();
        TenantSystemId system = newSystem(tenant);
        scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(system, "user_001", "Acme", "key-a")));

        assertThatThrownBy(() -> scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(system, "user_001", "Acme again", "key-b"))))
                .isInstanceOf(ConflictException.class);
        assertThat(owner.count("select count(*) from customers where tenant_id = ?", tenant.id().value()))
                .as("the failed attempt left nothing behind").isEqualTo(1);
        assertThat(owner.count("select count(*) from idempotency_keys where tenant_id = ? and scope = 'customer.create'", tenant.id().value())).isEqualTo(1);
    }

    @Test
    void aCustomerCanBeCreatedWithoutAMapping() {
        Tenant tenant = scopes.newTenant();
        Customer customer = scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(null, null, "Walk-in", "k"))).customer();
        assertThat(customer.identities()).isEmpty();
        assertThatThrownBy(() -> scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(null, "user_001", "Half", "k2"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMappingToAnotherTenantsSystemIsRejectedByTheDatabase() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        TenantSystemId systemOfB = newSystem(b);

        assertThatThrownBy(() -> scopes.inTenant(a.id(), () -> createCustomer.execute(command(systemOfB, "user_001", "Sneaky", "k"))))
                .hasMessageContaining("foreign key");
        assertThat(owner.count("select count(*) from customers where tenant_id = ?", a.id().value())).isZero();
    }

    @Test
    void anUnknownCustomerIsNotFound() {
        Tenant tenant = scopes.newTenant();
        assertThatThrownBy(() -> scopes.inTenant(tenant.id(), () -> customerQueries.get(new CustomerId(ids.newId()))))
                .isInstanceOf(NotFoundException.class);
        assertThat(clock.now()).isNotNull();
    }

    @Test
    void concurrentDuplicatesProduceOneCustomer() throws Exception {
        Tenant tenant = scopes.newTenant();
        TenantSystemId system = newSystem(tenant);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            var futures = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(i -> CompletableFuture.supplyAsync(
                            () -> scopes.inTenant(tenant.id(), () -> createCustomer.execute(command(system, "user_001", "Acme", "parallel-key"))), pool))
                    .toList();
            var ids = new java.util.HashSet<CustomerId>();
            for (var future : futures) {
                ids.add(future.get(30, TimeUnit.SECONDS).customer().id());
            }
            assertThat(ids).hasSize(1);
            assertThat(owner.count("select count(*) from customers where tenant_id = ?", tenant.id().value())).isEqualTo(1);
            assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'customer.created'", tenant.id().value())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
