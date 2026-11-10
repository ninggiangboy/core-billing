package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.money.api.wallet.CreditWallet;
import dev.ngb.core_billing.money.api.wallet.DebitWallet;
import dev.ngb.core_billing.money.api.reservation.ReleaseReservation;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.payment.adapter.persistence.incident.JdbcIncidentRepository;
import dev.ngb.core_billing.payment.adapter.persistence.payment.JdbcPaymentRepository;
import dev.ngb.core_billing.payment.adapter.persistence.refund.JdbcRefundRepository;
import dev.ngb.core_billing.payment.adapter.persistence.stripe.JdbcStripeConfigurationRepository;
import dev.ngb.core_billing.payment.adapter.persistence.stripe.JdbcStripeEventRepository;
import dev.ngb.core_billing.payment.adapter.persistence.stripe.JdbcStripeRoutes;
import dev.ngb.core_billing.payment.adapter.persistence.incident.JdbcIncidentReadRepository;
import dev.ngb.core_billing.payment.adapter.persistence.payment.JdbcPaymentReadRepository;
import dev.ngb.core_billing.payment.adapter.persistence.refund.JdbcRefundReadRepository;
import dev.ngb.core_billing.payment.adapter.persistence.stripe.JdbcStripeConfigurationReadRepository;
import dev.ngb.core_billing.payment.adapter.persistence.stripe.JdbcStripeEventReadRepository;
import dev.ngb.core_billing.payment.adapter.stripe.StripePaymentProvider;
import dev.ngb.core_billing.payment.application.payment.command.CreateTopUpUseCase;
import dev.ngb.core_billing.payment.application.incident.port.IncidentRepository;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.payment.query.PaymentQueries;
import dev.ngb.core_billing.payment.application.payment.query.PaymentReadRepository;
import dev.ngb.core_billing.payment.application.refund.query.RefundQueries;
import dev.ngb.core_billing.payment.application.refund.query.RefundReadRepository;
import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationQueries;
import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationReadRepository;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventQueries;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventReadRepository;
import dev.ngb.core_billing.payment.application.incident.query.IncidentQueries;
import dev.ngb.core_billing.payment.application.incident.query.IncidentReadRepository;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.payment.application.payment.command.PaymentSettings;
import dev.ngb.core_billing.payment.application.shared.command.PaymentSettlement;
import dev.ngb.core_billing.payment.application.stripe.port.ProviderAccess;
import dev.ngb.core_billing.payment.application.stripe.command.ReceiveStripeWebhookUseCase;
import dev.ngb.core_billing.payment.application.stripe.command.ReconcileStripeUseCase;
import dev.ngb.core_billing.payment.application.refund.port.RefundRepository;
import dev.ngb.core_billing.payment.application.refund.command.RequestRefundUseCase;
import dev.ngb.core_billing.payment.application.stripe.command.RetryStripeEventsUseCase;
import dev.ngb.core_billing.payment.application.stripe.port.SecretCipher;
import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.core_billing.payment.application.stripe.command.StripeConfigurationService;
import dev.ngb.core_billing.payment.application.stripe.command.StripeEventAdministration;
import dev.ngb.core_billing.payment.application.stripe.command.StripeEventProcessor;
import dev.ngb.core_billing.payment.application.stripe.port.StripeEventRepository;
import dev.ngb.core_billing.payment.application.stripe.port.StripeRoutes;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Backoff;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
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
    StripeConfigurationRepository stripeConfigurationRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcStripeConfigurationRepository(aggregates, jdbc);
    }

    @Bean
    StripeRoutes stripeRoutes(JdbcAggregateOperations aggregates, JdbcClient jdbc, Clock clock) {
        return new JdbcStripeRoutes(aggregates, jdbc, clock);
    }

    @Bean
    PaymentRepository paymentRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcPaymentRepository(aggregates, jdbc);
    }

    @Bean
    RefundRepository refundRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcRefundRepository(aggregates, jdbc);
    }

    @Bean
    StripeEventRepository stripeEventRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        return new JdbcStripeEventRepository(aggregates, jdbc);
    }

    @Bean
    IncidentRepository incidentRepository(JdbcClient jdbc) {
        return new JdbcIncidentRepository(jdbc);
    }

    @Bean
    PaymentReadRepository paymentReadRepository(JdbcClient jdbc) {
        return new JdbcPaymentReadRepository(jdbc);
    }

    @Bean
    RefundReadRepository refundReadRepository(JdbcClient jdbc) {
        return new JdbcRefundReadRepository(jdbc);
    }

    @Bean
    StripeConfigurationReadRepository stripeConfigurationReadRepository(JdbcClient jdbc) {
        return new JdbcStripeConfigurationReadRepository(jdbc);
    }

    @Bean
    StripeEventReadRepository stripeEventReadRepository(JdbcClient jdbc) {
        return new JdbcStripeEventReadRepository(jdbc);
    }

    @Bean
    IncidentReadRepository incidentReadRepository(JdbcClient jdbc) {
        return new JdbcIncidentReadRepository(jdbc);
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
            CustomerLookup customers, PaymentProvider provider, ProviderAccess access, PaymentSettings settings, AuditLog auditLog,
            TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new CreateTopUpUseCase(unitOfWork, configurations, payments, wallets, customers, provider, access, settings, auditLog, tenantContext,
                ids, clock);
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
    PaymentQueries paymentQueries(
            UnitOfWork unitOfWork, PaymentReadRepository payments, RefundReadRepository refunds, CustomerLookup customers) {
        return new PaymentQueries(unitOfWork, payments, refunds, customers);
    }

    @Bean
    RefundQueries refundQueries(
            UnitOfWork unitOfWork, RefundReadRepository refunds, PaymentReadRepository payments, CustomerLookup customers) {
        return new RefundQueries(unitOfWork, refunds, payments, customers);
    }

    @Bean
    StripeConfigurationQueries stripeConfigurationQueries(UnitOfWork unitOfWork, StripeConfigurationReadRepository configurations) {
        return new StripeConfigurationQueries(unitOfWork, configurations);
    }

    @Bean
    StripeEventQueries stripeEventQueries(UnitOfWork unitOfWork, StripeEventReadRepository events) {
        return new StripeEventQueries(unitOfWork, events);
    }

    @Bean
    IncidentQueries incidentQueries(UnitOfWork unitOfWork, IncidentReadRepository incidents) {
        return new IncidentQueries(unitOfWork, incidents);
    }
}
