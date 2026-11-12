package dev.ngb.sampletenant.boot.bootstrap;

import dev.ngb.sampletenant.billing.adapter.client.http.HttpBillingClient;
import dev.ngb.sampletenant.billing.adapter.client.http.HttpBillingSettings;
import dev.ngb.sampletenant.billing.api.client.BillingAdmin;
import dev.ngb.sampletenant.billing.api.client.BillingWebhooks;
import dev.ngb.sampletenant.billing.api.model.PricingPlan;
import dev.ngb.sampletenant.catalog.application.plan.command.AttachBillingPlanUseCase;
import dev.ngb.sampletenant.catalog.application.plan.query.CatalogQueries;
import dev.ngb.sampletenant.catalog.application.plan.query.PlanView;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot reference provisioning: it publishes a Billing plan for every catalog plan that has no
 * binding and registers this tenant's webhook endpoint. It is off by default and safe to repeat.
 */
@Component
@ConditionalOnProperty(name = "sampletenant.bootstrap.enabled", havingValue = "true")
class BootstrapRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapRunner.class);

    private final CatalogQueries catalog;
    private final AttachBillingPlanUseCase attach;
    private final HttpBillingSettings tenantSettings;
    private final String adminToken;
    private final String webhookUrl;
    private final List<String> eventTypes;

    BootstrapRunner(
            CatalogQueries catalog,
            AttachBillingPlanUseCase attach,
            HttpBillingSettings tenantSettings,
            @Value("${sampletenant.bootstrap.admin-token:}") String adminToken,
            @Value("${sampletenant.bootstrap.webhook-url:http://localhost:8082/webhooks/billing}") String webhookUrl,
            @Value("${sampletenant.bootstrap.webhook-event-types:}") String eventTypes) {
        this.catalog = catalog;
        this.attach = attach;
        this.tenantSettings = tenantSettings;
        this.adminToken = adminToken;
        this.webhookUrl = webhookUrl;
        this.eventTypes = eventTypes.isBlank() ? List.of() : Arrays.stream(eventTypes.split(",")).map(String::trim).toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        publishPlans();
        registerWebhook();
    }

    private void publishPlans() {
        if (adminToken.isBlank()) {
            LOG.warn("sampletenant.bootstrap.admin-token is empty; pricing plans were not published");
            return;
        }
        BillingAdmin admin = new HttpBillingClient(new HttpBillingSettings(
                tenantSettings.baseUrl(),
                adminToken,
                tenantSettings.connectTimeout(),
                tenantSettings.readTimeout(),
                tenantSettings.maxAttempts(),
                tenantSettings.retryBackoff()));
        List<PricingPlan> existing = admin.plans(null);
        for (PlanView plan : catalog.all()) {
            if (plan.billingPlanId() != null && !plan.billingPlanId().isBlank()) {
                continue;
            }
            String billingPlanId = existing.stream()
                    .filter(candidate -> candidate.code().equals(plan.code()))
                    .map(PricingPlan::id)
                    .findFirst()
                    .orElseGet(() -> admin.createPlan(ReferencePlans.request(plan)).id());
            attach.attachByCode(plan.code(), billingPlanId);
            LOG.info("catalog plan {} bound to Billing plan {}", plan.code(), billingPlanId);
        }
    }

    private void registerWebhook() {
        if (tenantSettings.apiKey().isBlank()) {
            LOG.warn("sampletenant.billing.api-key is empty; the webhook endpoint was not registered");
            return;
        }
        HttpBillingClient tenant = new HttpBillingClient(tenantSettings);
        boolean exists = tenant.endpoints().stream().anyMatch(endpoint -> endpoint.url().equals(webhookUrl));
        if (exists) {
            return;
        }
        BillingWebhooks.EndpointWithSecret created =
                tenant.createEndpoint(new BillingWebhooks.CreateEndpointRequest(webhookUrl, "Sample tenant", eventTypes));
        LOG.warn("store this signing secret as SAMPLE_TENANT_WEBHOOK_SECRET: {}", created.signingSecret());
    }
}
