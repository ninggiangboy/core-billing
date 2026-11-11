package dev.ngb.core_billing.payment;

import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.sharedkernel.SecretCipher;
import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.core_billing.payment.application.stripe.port.StripeRoutes;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEnvironment;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds tenants with a customer, a wallet and a working Direct Stripe configuration for the payment tests. */
@Component
public class PaymentTestWorld {

    /** A tenant, its money world and its Stripe configuration; {@code webhookSecret} is what Stripe would sign with. */
    public record PayWorld(World money, StripeConfiguration configuration, String webhookSecret) {}

    private final MoneyTestWorld moneyWorld;
    private final ScopeTestSupport scopes;
    private final UnitOfWork unitOfWork;
    private final StripeConfigurationRepository configurations;
    private final StripeRoutes routes;
    private final PaymentRepository payments;
    private final SecretCipher cipher;
    private final Clock clock;

    PaymentTestWorld(
            MoneyTestWorld moneyWorld, ScopeTestSupport scopes, UnitOfWork unitOfWork, StripeConfigurationRepository configurations, StripeRoutes routes,
            PaymentRepository payments, SecretCipher cipher, Clock clock) {
        this.moneyWorld = moneyWorld;
        this.scopes = scopes;
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
        this.routes = routes;
        this.payments = payments;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** A tenant with a customer and a funded wallet but no Stripe configuration yet. */
    public World tenantWithoutStripe(long walletBalance) {
        return moneyWorld.prepaid(walletBalance);
    }

    public PayWorld direct(long walletBalance) {
        return directFor(moneyWorld.prepaid(walletBalance), "acct_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
    }

    /** A Direct configuration for an existing tenant, on the given Stripe account. */
    public PayWorld directFor(World money, String accountId) {
        String webhookSecret = "whsec_" + UUID.randomUUID().toString().replace("-", "");
        StripeConfigurationId id = new StripeConfigurationId(UUID.randomUUID());
        StripeConfiguration configuration = scopes.inTenant(money.tenant().id(), () -> unitOfWork.transactional(() -> {
            StripeConfiguration c = StripeConfiguration.direct(id, money.tenant().id(), accountId, StripeEnvironment.TEST,
                    cipher.encrypt("sk_test_" + UUID.randomUUID().toString().replace("-", ""), id.toString()), cipher.encrypt(webhookSecret, id.toString()),
                    "pk_test_x", clock.now());
            configurations.add(c);
            routes.register(new StripeRoutes.Route(id, money.tenant().id(), accountId));
            return c;
        }));
        return new PayWorld(money, configuration, webhookSecret);
    }

    /** A tenant whose Stripe account is linked through Connect and already allowed to take card payments. */
    public PayWorld connected(long walletBalance) {
        World money = moneyWorld.prepaid(walletBalance);
        String accountId = "acct_conn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        StripeConfigurationId id = new StripeConfigurationId(UUID.randomUUID());
        StripeConfiguration configuration = scopes.inTenant(money.tenant().id(), () -> unitOfWork.transactional(() -> {
            StripeConfiguration c = StripeConfiguration.connect(id, money.tenant().id(), accountId, StripeEnvironment.TEST, clock.now());
            c.observeAccount(true, clock.now());
            configurations.add(c);
            routes.register(new StripeRoutes.Route(id, money.tenant().id(), accountId));
            return c;
        }));
        return new PayWorld(money, configuration, null);
    }

    /** A payment already stored the way a top-up leaves it before the customer pays. */
    public Payment openPayment(PayWorld world, PaymentId id, long amount) {
        return scopes.inTenant(world.money().tenant().id(), () -> unitOfWork.transactional(() -> {
            Payment payment = Payment.start(id, world.configuration(), world.money().wallet().id(), world.money().customerId(),
                    Money.of(amount, world.money().wallet().currency()), "key-" + id, "hash-" + id, clock.now());
            payments.add(payment);
            return payment;
        }));
    }
}
