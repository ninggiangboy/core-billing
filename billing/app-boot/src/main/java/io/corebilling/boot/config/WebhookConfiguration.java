package io.corebilling.boot.config;

import io.corebilling.boot.infrastructure.AesGcmSecretCipher;
import io.corebilling.boot.infrastructure.SchemaEventTypeCatalog;
import io.corebilling.boot.infrastructure.outbox.OutboxRelay;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Backoff;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventInbox;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.webhook.adapter.crypto.HmacSha256Signer;
import io.corebilling.webhook.adapter.crypto.RandomSigningKeys;
import io.corebilling.webhook.adapter.http.HttpComponentsWebhookSender;
import io.corebilling.webhook.adapter.messaging.JacksonEventBodyWriter;
import io.corebilling.webhook.adapter.messaging.WebhookEventListener;
import io.corebilling.webhook.adapter.persistence.JdbcWebhookDeliveryRepository;
import io.corebilling.webhook.adapter.persistence.JdbcWebhookEndpointRepository;
import io.corebilling.webhook.application.CreateEndpointUseCase;
import io.corebilling.webhook.application.DeliverWebhooksUseCase;
import io.corebilling.webhook.application.DeliveryAdministration;
import io.corebilling.webhook.application.EndpointAdministration;
import io.corebilling.webhook.application.EventBodyWriter;
import io.corebilling.webhook.application.EventTypeCatalog;
import io.corebilling.webhook.application.PayloadSigner;
import io.corebilling.webhook.application.RecordDeliveriesUseCase;
import io.corebilling.webhook.application.SecretCipher;
import io.corebilling.webhook.application.SigningKeys;
import io.corebilling.webhook.application.WebhookDeliveryRepository;
import io.corebilling.webhook.application.WebhookEndpointRepository;
import io.corebilling.webhook.application.WebhookSender;
import io.corebilling.webhook.application.WebhookSettings;
import io.corebilling.webhook.domain.DeliveryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** Wires the outbox relay and the webhook module. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class WebhookConfiguration {

    @Bean
    OutboxRelay outboxRelay(
            UnitOfWork unitOfWork, JdbcClient jdbc, JsonMapper json, ApplicationEventPublisher publisher, Clock clock, MeterRegistry meters,
            @Value("${billing.outbox.batch-size:100}") int batchSize,
            @Value("${billing.outbox.max-attempts:10}") int maxAttempts,
            @Value("${billing.outbox.backoff-base:PT10S}") Duration backoffBase,
            @Value("${billing.outbox.backoff-cap:PT1H}") Duration backoffCap) {
        return new OutboxRelay(unitOfWork, jdbc, json, publisher, clock, meters, batchSize, maxAttempts, new Backoff(backoffBase, backoffCap, 0.2));
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
    WebhookEndpointRepository webhookEndpointRepository(JdbcClient jdbc) {
        return new JdbcWebhookEndpointRepository(jdbc);
    }

    @Bean
    WebhookDeliveryRepository webhookDeliveryRepository(JdbcClient jdbc) {
        return new JdbcWebhookDeliveryRepository(jdbc);
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
            @Value("${billing.webhooks.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${billing.webhooks.read-timeout:PT10S}") Duration readTimeout,
            @Value("${billing.webhooks.deadline:PT15S}") Duration deadline) {
        return new HttpComponentsWebhookSender(settings.allowInsecureTargets(), connectTimeout, readTimeout, deadline);
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
    EndpointAdministration endpointAdministration(
            UnitOfWork unitOfWork, WebhookEndpointRepository endpoints, SecretCipher cipher, SigningKeys keys, EventTypeCatalog catalog,
            WebhookSettings settings, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        return new EndpointAdministration(unitOfWork, endpoints, cipher, keys, catalog, settings, auditLog, tenantContext, clock);
    }

    @Bean
    DeliveryAdministration deliveryAdministration(
            UnitOfWork unitOfWork, WebhookDeliveryRepository deliveries, WebhookEndpointRepository endpoints, AuditLog auditLog,
            TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new DeliveryAdministration(unitOfWork, deliveries, endpoints, auditLog, tenantContext, ids, clock);
    }

    @Bean
    RecordDeliveriesUseCase recordDeliveriesUseCase(
            UnitOfWork unitOfWork, WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries, EventInbox inbox, EventBodyWriter bodies,
            IdGenerator ids, Clock clock) {
        return new RecordDeliveriesUseCase(unitOfWork, endpoints, deliveries, inbox, bodies, ids, clock);
    }

    @Bean
    DeliverWebhooksUseCase deliverWebhooksUseCase(
            UnitOfWork unitOfWork, WebhookDeliveryRepository deliveries, SecretCipher cipher, PayloadSigner signer, WebhookSender sender,
            WebhookSettings settings, Clock clock) {
        return new DeliverWebhooksUseCase(unitOfWork, deliveries, cipher, signer, sender, settings, clock, () -> ThreadLocalRandom.current().nextDouble());
    }

    @Bean
    WebhookEventListener webhookEventListener(RecordDeliveriesUseCase recordDeliveries) {
        return new WebhookEventListener(recordDeliveries);
    }
}
