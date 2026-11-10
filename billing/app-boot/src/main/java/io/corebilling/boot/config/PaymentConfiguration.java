package io.corebilling.boot.config;

import io.corebilling.money.api.wallet.CreditWallet;
import io.corebilling.money.api.wallet.DebitWallet;
import io.corebilling.money.api.reservation.ReleaseReservation;
import io.corebilling.money.api.reservation.ReserveBudget;
import io.corebilling.money.api.wallet.WalletLookup;
import io.corebilling.payment.adapter.persistence.JdbcIncidentRepository;
import io.corebilling.payment.adapter.persistence.JdbcPaymentRepository;
import io.corebilling.payment.adapter.persistence.JdbcRefundRepository;
import io.corebilling.payment.adapter.persistence.JdbcStripeConfigurationRepository;
import io.corebilling.payment.adapter.persistence.JdbcStripeEventRepository;
import io.corebilling.payment.adapter.persistence.JdbcStripeRoutes;
import io.corebilling.payment.adapter.stripe.StripePaymentProvider;
import io.corebilling.payment.application.payment.command.CreateTopUpUseCase;
import io.corebilling.payment.application.incident.port.IncidentRepository;
import io.corebilling.payment.application.payment.port.PaymentProvider;
import io.corebilling.payment.application.payment.query.PaymentQueries;
import io.corebilling.payment.application.payment.port.PaymentRepository;
import io.corebilling.payment.application.payment.command.PaymentSettings;
import io.corebilling.payment.application.shared.command.PaymentSettlement;
import io.corebilling.payment.application.stripe.port.ProviderAccess;
import io.corebilling.payment.application.stripe.command.ReceiveStripeWebhookUseCase;
import io.corebilling.payment.application.stripe.command.ReconcileStripeUseCase;
import io.corebilling.payment.application.refund.port.RefundRepository;
import io.corebilling.payment.application.refund.command.RequestRefundUseCase;
import io.corebilling.payment.application.stripe.command.RetryStripeEventsUseCase;
import io.corebilling.payment.application.stripe.port.SecretCipher;
import io.corebilling.payment.application.stripe.port.StripeConfigurationRepository;
import io.corebilling.payment.application.stripe.command.StripeConfigurationService;
import io.corebilling.payment.application.stripe.command.StripeEventAdministration;
import io.corebilling.payment.application.stripe.command.StripeEventProcessor;
import io.corebilling.payment.application.stripe.port.StripeEventRepository;
import io.corebilling.payment.application.stripe.port.StripeRoutes;
import io.corebilling.platform.api.audit.AuditLog;
import io.corebilling.platform.api.tenant.TenantContext;
import io.corebilling.sharedkernel.Backoff;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the payment module and its Stripe adapter. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class PaymentConfiguration {

    @Bean
    PaymentSettings paymentSettings(
            @Value("${billing.payments.public-base-url}") String publicBaseUrl,
            @Value("${billing.payments.checkout-success-url}") String successUrl,
            @Value("${billing.payments.checkout-cancel-url}") String cancelUrl,
            @Value("${billing.payments.refund-hold:P7D}") Duration refundHold,
            @Value("${billing.payments.event-max-attempts:8}") int maxAttempts,
            @Value("${billing.payments.event-backoff-base:PT30S}") Duration backoffBase,
            @Value("${billing.payments.event-backoff-cap:PT6H}") Duration backoffCap,
            @Value("${billing.payments.event-batch-size:25}") int batchSize,
            @Value("${billing.payments.reconcile-lookback:P3D}") Duration lookback,
            @Value("${billing.payments.connect.platform-secret-key:}") String connectKey,
            @Value("${billing.payments.connect.webhook-secret:}") String connectWebhookSecret,
            @Value("${billing.payments.connect.onboarding-return-url:}") String returnUrl,
            @Value("${billing.payments.connect.onboarding-refresh-url:}") String refreshUrl) {
        PaymentSettings.Connect connect = connectKey.isBlank() || connectWebhookSecret.isBlank()
                ? null
                : new PaymentSettings.Connect(connectKey, connectWebhookSecret, returnUrl, refreshUrl);
        return new PaymentSettings(publicBaseUrl, connect, successUrl, cancelUrl, refundHold, maxAttempts, new Backoff(backoffBase, backoffCap, 0.2),
                batchSize, lookback);
    }

    @Bean
    PaymentProvider paymentProvider(
            @Value("${billing.payments.stripe.api-base:}") String apiBase,
            @Value("${billing.payments.stripe.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${billing.payments.stripe.read-timeout:PT30S}") Duration readTimeout,
            @Value("${billing.payments.stripe.webhook-tolerance:PT5M}") Duration tolerance) {
        return new StripePaymentProvider(apiBase.isBlank() ? null : apiBase, connectTimeout, readTimeout, tolerance.toSeconds());
    }

    @Bean
    ProviderAccess providerAccess(SecretCipher cipher, PaymentSettings settings) {
        return new ProviderAccess(cipher, settings);
    }

    @Bean
    StripeConfigurationRepository stripeConfigurationRepository(JdbcClient jdbc) {
        return new JdbcStripeConfigurationRepository(jdbc);
    }

    @Bean
    StripeRoutes stripeRoutes(JdbcClient jdbc, Clock clock) {
        return new JdbcStripeRoutes(jdbc, clock);
    }

    @Bean
    PaymentRepository paymentRepository(JdbcClient jdbc) {
        return new JdbcPaymentRepository(jdbc);
    }

    @Bean
    RefundRepository refundRepository(JdbcClient jdbc) {
        return new JdbcRefundRepository(jdbc);
    }

    @Bean
    StripeEventRepository stripeEventRepository(JdbcClient jdbc) {
        return new JdbcStripeEventRepository(jdbc);
    }

    @Bean
    IncidentRepository incidentRepository(JdbcClient jdbc) {
        return new JdbcIncidentRepository(jdbc);
    }

    @Bean
    PaymentSettlement paymentSettlement(
            PaymentRepository payments, RefundRepository refunds, IncidentRepository incidents, StripeConfigurationRepository configurations,
            CreditWallet credit, DebitWallet debit, ReleaseReservation release, EventPublisher events, IdGenerator ids, Clock clock) {
        return new PaymentSettlement(payments, refunds, incidents, configurations, credit, debit, release, events, ids, clock);
    }

    @Bean
    StripeEventProcessor stripeEventProcessor(
            UnitOfWork unitOfWork, StripeEventRepository events, StripeConfigurationRepository configurations, IncidentRepository incidents,
            PaymentProvider provider, ProviderAccess access, PaymentSettlement settlement, PaymentSettings settings, IdGenerator ids, Clock clock) {
        return new StripeEventProcessor(unitOfWork, events, configurations, incidents, provider, access, settlement, settings, ids, clock,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    @Bean
    ReceiveStripeWebhookUseCase receiveStripeWebhookUseCase(
            UnitOfWork unitOfWork, TenantContext tenantContext, StripeRoutes routes, StripeConfigurationRepository configurations,
            StripeEventRepository events, IncidentRepository incidents, PaymentProvider provider, StripeEventProcessor processor, SecretCipher cipher,
            PaymentSettings settings, IdGenerator ids, Clock clock) {
        return new ReceiveStripeWebhookUseCase(unitOfWork, tenantContext, routes, configurations, events, incidents, provider, processor, cipher,
                settings, ids, clock);
    }

    @Bean
    RetryStripeEventsUseCase retryStripeEventsUseCase(
            UnitOfWork unitOfWork, StripeEventRepository events, StripeEventProcessor processor, PaymentSettings settings, Clock clock) {
        return new RetryStripeEventsUseCase(unitOfWork, events, processor, settings, clock);
    }

    @Bean
    StripeEventAdministration stripeEventAdministration(
            UnitOfWork unitOfWork, StripeEventRepository events, IncidentRepository incidents, StripeEventProcessor processor, AuditLog auditLog,
            TenantContext tenantContext, Clock clock) {
        return new StripeEventAdministration(unitOfWork, events, incidents, processor, auditLog, tenantContext, clock);
    }

    @Bean
    StripeConfigurationService stripeConfigurationService(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, StripeRoutes routes, PaymentProvider provider, SecretCipher cipher,
            ProviderAccess access, PaymentSettings settings, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new StripeConfigurationService(unitOfWork, configurations, routes, provider, cipher, access, settings, auditLog, tenantContext, ids, clock);
    }

    @Bean
    CreateTopUpUseCase createTopUpUseCase(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, PaymentRepository payments, WalletLookup wallets,
            PaymentProvider provider, ProviderAccess access, PaymentSettings settings, AuditLog auditLog, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        return new CreateTopUpUseCase(unitOfWork, configurations, payments, wallets, provider, access, settings, auditLog, tenantContext, ids, clock);
    }

    @Bean
    RequestRefundUseCase requestRefundUseCase(
            UnitOfWork unitOfWork, PaymentRepository payments, RefundRepository refunds, StripeConfigurationRepository configurations,
            ReserveBudget reserve, PaymentSettlement settlement, PaymentProvider provider, ProviderAccess access, PaymentSettings settings,
            AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new RequestRefundUseCase(unitOfWork, payments, refunds, configurations, reserve, settlement, provider, access, settings, auditLog,
                tenantContext, ids, clock);
    }

    @Bean
    ReconcileStripeUseCase reconcileStripeUseCase(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, PaymentRepository payments, RefundRepository refunds,
            IncidentRepository incidents, PaymentProvider provider, ProviderAccess access, PaymentSettlement settlement, PaymentSettings settings,
            IdGenerator ids, Clock clock) {
        return new ReconcileStripeUseCase(unitOfWork, configurations, payments, refunds, incidents, provider, access, settlement, settings, ids, clock);
    }

    @Bean
    PaymentQueries paymentQueries(UnitOfWork unitOfWork, PaymentRepository payments, RefundRepository refunds) {
        return new PaymentQueries(unitOfWork, payments, refunds);
    }
}
