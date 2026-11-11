package dev.ngb.core_billing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.payment.PaymentTestWorld.PayWorld;
import dev.ngb.core_billing.payment.application.incident.query.IncidentQueries;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.stripe.command.ReconcileStripeUseCase;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.support.FakePaymentProvider;
import dev.ngb.core_billing.support.FakePaymentProviderConfig;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The worker's short lock budget must not turn a busy payment into an incident for a person to chase: the item is
 * neither a mismatch nor an incident, and the next run examines it again once the lock is free.
 */
@SpringBootTest(
        classes = Application.class,
        properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '300ms'")
@Import({PostgresContainerConfig.class, FakePaymentProviderConfig.class})
class ReconciliationLockContentionIntegrationTest {

    @Autowired PaymentTestWorld worlds;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired FakePaymentProvider stripe;
    @Autowired ReconcileStripeUseCase reconcile;
    @Autowired IncidentQueries incidents;

    @Test
    void aPaymentLockWaitIsLeftForTheNextRunInsteadOfBecomingAnIncident() throws SQLException {
        PayWorld world = worlds.direct(0);
        Payment payment = worlds.openPayment(world, new PaymentId(UUID.randomUUID()), 5_000);
        stripe.recentCheckouts.add(new PaymentProvider.Checkout(payment.checkoutSessionId(), "pi_" + UUID.randomUUID(), payment.id().toString(),
                PaymentProvider.CheckoutStatus.COMPLETE, PaymentProvider.CheckoutPayment.PAID, 5_000, "usd"));

        try (Connection held = owner.jdbc().getDataSource().getConnection()) {
            held.setAutoCommit(false);
            try (Statement lock = held.createStatement()) {
                lock.execute("select id from payments where id = '%s' for update".formatted(payment.id().value()));
            }
            try {
                assertThat(scopes.inTenant(world.money().tenant().id(), reconcile::runOnce))
                        .as("a lock wait that times out is not a mismatch")
                        .isEqualTo(new ReconcileStripeUseCase.Report(1, 0, 0));
            } finally {
                held.rollback();
            }
        }

        assertThat(scopes.inTenant(world.money().tenant().id(), () -> incidents.list(true, 10)))
                .as("no incident is opened for a payment that was merely locked")
                .isEmpty();

        assertThat(scopes.inTenant(world.money().tenant().id(), reconcile::runOnce).corrected())
                .as("the next run examines the payment once the lock is free")
                .isEqualTo(1);
    }
}
