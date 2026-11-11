package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.boot.infrastructure.security.AesGcmSecretCipher;
import dev.ngb.core_billing.boot.infrastructure.messaging.SchemaEventTypeCatalog;
import dev.ngb.core_billing.boot.infrastructure.messaging.OutboxRelay;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.util.Backoff;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.event.EventInbox;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import dev.ngb.core_billing.webhook.adapter.delivery.crypto.HmacSha256Signer;
import dev.ngb.core_billing.webhook.adapter.endpoint.crypto.RandomSigningKeys;
import dev.ngb.core_billing.webhook.adapter.delivery.http.HttpComponentsWebhookSender;
import dev.ngb.core_billing.webhook.adapter.delivery.json.JacksonEventBodyWriter;
import dev.ngb.core_billing.webhook.adapter.delivery.messaging.WebhookEventListener;
import dev.ngb.core_billing.webhook.adapter.delivery.persistence.JooqWebhookDeliveryRepository;
import dev.ngb.core_billing.webhook.adapter.endpoint.persistence.JooqWebhookEndpointRepository;
import dev.ngb.core_billing.webhook.adapter.delivery.persistence.JooqWebhookDeliveryReadRepository;
import dev.ngb.core_billing.webhook.adapter.endpoint.persistence.JooqWebhookEndpointReadRepository;
import dev.ngb.core_billing.webhook.application.delivery.query.DeliveryQueries;
import dev.ngb.core_billing.webhook.application.delivery.query.DeliveryReadRepository;
import dev.ngb.core_billing.webhook.application.endpoint.query.EndpointQueries;
import dev.ngb.core_billing.webhook.application.endpoint.query.EndpointReadRepository;
import dev.ngb.core_billing.webhook.application.endpoint.command.CreateEndpointUseCase;
import dev.ngb.core_billing.webhook.application.delivery.command.DeliverWebhooksUseCase;
import dev.ngb.core_billing.webhook.application.delivery.command.ManageDeliveryUseCase;
import dev.ngb.core_billing.webhook.application.endpoint.command.ManageEndpointUseCase;
import dev.ngb.core_billing.webhook.application.delivery.port.EventBodyWriter;
import dev.ngb.core_billing.webhook.application.delivery.port.DeliveryLagRecorder;
import dev.ngb.core_billing.webhook.application.endpoint.port.EventTypeCatalog;
import dev.ngb.core_billing.webhook.application.delivery.port.PayloadSigner;
import dev.ngb.core_billing.webhook.application.delivery.command.RecordDeliveriesUseCase;
import dev.ngb.core_billing.sharedkernel.security.SecretCipher;
import dev.ngb.core_billing.webhook.application.endpoint.port.SigningKeys;
import dev.ngb.core_billing.webhook.application.delivery.port.WebhookDeliveryRepository;
import dev.ngb.core_billing.webhook.application.endpoint.port.WebhookEndpointRepository;
import dev.ngb.core_billing.webhook.application.delivery.port.WebhookSender;
import dev.ngb.core_billing.webhook.application.endpoint.service.WebhookSettings;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.jooq.DSLContext;
import tools.jackson.databind.json.JsonMapper;

/** Wires the outbox relay and the webhook module. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class WebhookConfiguration {

    @Bean
    OutboxRelay outboxRelay(
            UnitOfWork unitOfWork, DSLContext dsl, JsonMapper json, ApplicationEventPublisher publisher, Clock clock, MeterRegistry meters,
            @Value("${billing.outbox.batch-size:100}") int batchSize,
            @Value("${billing.outbox.max-attempts:10}") int maxAttempts,
            @Value("${billing.outbox.backoff-base:PT10S}") Duration backoffBase,
            @Value("${billing.outbox.backoff-cap:PT1H}") Duration backoffCap) {
        return new OutboxRelay(unitOfWork, dsl, json, publisher, clock, meters, batchSize, maxAttempts, new Backoff(backoffBase, backoffCap, 0.2));
    }

    @Bean
    WebhookSettings webhookSettings(
            @Value("${billing.webhooks.allow-insecure-targets:false}") boolean allowInsecureTargets,
            @Value("${billing.webhooks.batch-size:25}") int batchSize,
            @Value("${billing.webhooks.max-attempts:10}") int maxAttempts,
            @Value("${billing.webhooks.backoff-base:PT30S}") Duration backoffBase,
            @Value("${billing.webhooks.backoff-cap:PT6H}") Duration backoffCap,
            @Value("${billing.webhooks.lease:PT2M}") Duration lease) {
        return new WebhookSettings(allowInsecureTargets, batchSize, new DeliveryPolicy(maxAttempts, new Backoff(backoffBase, backoffCap, 0.2), lease));
    }

    @Bean
    WebhookEndpointRepository webhookEndpointRepository(DSLContext dsl) {
        return new JooqWebhookEndpointRepository(dsl);
    }

    @Bean
    WebhookDeliveryRepository webhookDeliveryRepository(DSLContext dsl) {
        return new JooqWebhookDeliveryRepository(dsl);
    }

    @Bean
    EndpointReadRepository endpointReadRepository(DSLContext dsl) {
        return new JooqWebhookEndpointReadRepository(dsl);
    }

    @Bean
    DeliveryReadRepository deliveryReadRepository(DSLContext dsl) {
        return new JooqWebhookDeliveryReadRepository(dsl);
    }

    @Bean
    EndpointQueries endpointQueries(UnitOfWork unitOfWork, EndpointReadRepository endpoints) {
        return new EndpointQueries(unitOfWork, endpoints);
    }

    @Bean
    DeliveryQueries deliveryQueries(UnitOfWork unitOfWork, DeliveryReadRepository deliveries) {
        return new DeliveryQueries(unitOfWork, deliveries);
    }

    @Bean
    AesGcmSecretCipher secretCipher(
            @Value("${billing.secrets.master-key}") String masterKey, @Value("${billing.secrets.master-key-id:k1}") String masterKeyId) {
        return new AesGcmSecretCipher(masterKey, masterKeyId);
    }

    @Bean
    SigningKeys signingKeys() {
        return new RandomSigningKeys();
    }

    @Bean
    PayloadSigner payloadSigner() {
        return new HmacSha256Signer();
    }

    @Bean(destroyMethod = "close")
    HttpComponentsWebhookSender webhookSender(
            WebhookSettings settings,
            @Value("${billing.webhooks.max-connections:100}") int maxConnTotal,
            @Value("${billing.webhooks.max-connections-per-route:25}") int maxConnPerRoute,
            @Value("${billing.webhooks.connection-request-timeout:PT2S}") Duration connectionRequestTimeout,
            @Value("${billing.webhooks.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${billing.webhooks.read-timeout:PT10S}") Duration readTimeout,
            @Value("${billing.webhooks.deadline:PT15S}") Duration deadline) {
        return new HttpComponentsWebhookSender(
                settings.allowInsecureTargets(), maxConnTotal, maxConnPerRoute, connectTimeout, connectionRequestTimeout, readTimeout, deadline);
    }

    @Bean
    EventBodyWriter eventBodyWriter(JsonMapper json) {
        return new JacksonEventBodyWriter(json);
    }

    @Bean
    EventTypeCatalog eventTypeCatalog() {
        return new SchemaEventTypeCatalog();
    }

    @Bean
    CreateEndpointUseCase createEndpointUseCase(
            UnitOfWork unitOfWork, WebhookEndpointRepository endpoints, SecretCipher cipher, SigningKeys keys, EventTypeCatalog catalog,
            WebhookSettings settings, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new CreateEndpointUseCase(unitOfWork, endpoints, cipher, keys, catalog, settings, auditLog, tenantContext, ids, clock);
    }

    @Bean
    ManageEndpointUseCase manageEndpoint(
            UnitOfWork unitOfWork, WebhookEndpointRepository endpoints, SecretCipher cipher, SigningKeys keys, EventTypeCatalog catalog,
            WebhookSettings settings, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        return new ManageEndpointUseCase(unitOfWork, endpoints, cipher, keys, catalog, settings, auditLog, tenantContext, clock);
    }

    @Bean
    ManageDeliveryUseCase manageDelivery(
            UnitOfWork unitOfWork, WebhookDeliveryRepository deliveries, WebhookEndpointRepository endpoints, AuditLog auditLog,
            TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new ManageDeliveryUseCase(unitOfWork, deliveries, endpoints, auditLog, tenantContext, ids, clock);
    }

    @Bean
    RecordDeliveriesUseCase recordDeliveriesUseCase(
            UnitOfWork unitOfWork, WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries, EventInbox inbox, EventBodyWriter bodies,
            IdGenerator ids, Clock clock) {
        return new RecordDeliveriesUseCase(unitOfWork, endpoints, deliveries, inbox, bodies, ids, clock);
    }

    @Bean
    DeliveryLagRecorder deliveryLagRecorder(MeterRegistry meters) {
        return meters.timer("billing.webhook.lag")::record;
    }

    @Bean
    DeliverWebhooksUseCase deliverWebhooksUseCase(
            UnitOfWork unitOfWork, WebhookDeliveryRepository deliveries, SecretCipher cipher, PayloadSigner signer, WebhookSender sender,
            WebhookSettings settings, Clock clock, DeliveryLagRecorder lags) {
        return new DeliverWebhooksUseCase(
                unitOfWork, deliveries, cipher, signer, sender, settings, clock, lags, () -> ThreadLocalRandom.current().nextDouble());
    }

    @Bean
    WebhookEventListener webhookEventListener(RecordDeliveriesUseCase recordDeliveries) {
        return new WebhookEventListener(recordDeliveries);
    }
}
