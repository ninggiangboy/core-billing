package dev.ngb.tenant.billing.adapter.client.feign;

import dev.ngb.tenant.billing.api.client.BillingAdmin.CreatePlanRequest;
import dev.ngb.tenant.billing.api.client.BillingCharges.CaptureRequest;
import dev.ngb.tenant.billing.api.client.BillingCharges.ChargeRequest;
import dev.ngb.tenant.billing.api.client.BillingCustomers.NewCustomer;
import dev.ngb.tenant.billing.api.client.BillingPayments.TopUpRequest;
import dev.ngb.tenant.billing.api.client.BillingPricing.EstimateRequest;
import dev.ngb.tenant.billing.api.client.BillingPricing.ReserveRequest;
import dev.ngb.tenant.billing.api.client.BillingWallets.OpenWallet;
import dev.ngb.tenant.billing.api.client.BillingWebhooks.CreateEndpointRequest;
import dev.ngb.tenant.billing.api.client.BillingWebhooks.Endpoint;
import dev.ngb.tenant.billing.api.client.BillingWebhooks.EndpointWithSecret;
import dev.ngb.tenant.billing.api.model.BillingSession;
import dev.ngb.tenant.billing.api.model.Charge;
import dev.ngb.tenant.billing.api.model.ChargeUsageRequest;
import dev.ngb.tenant.billing.api.model.Customer;
import dev.ngb.tenant.billing.api.model.Estimate;
import dev.ngb.tenant.billing.api.model.Invoice;
import dev.ngb.tenant.billing.api.model.InvoiceDetail;
import dev.ngb.tenant.billing.api.model.Payment;
import dev.ngb.tenant.billing.api.model.PaymentDetail;
import dev.ngb.tenant.billing.api.model.PricingPlan;
import dev.ngb.tenant.billing.api.model.Rating;
import dev.ngb.tenant.billing.api.model.RecordedUsage;
import dev.ngb.tenant.billing.api.model.Reservation;
import dev.ngb.tenant.billing.api.model.ReserveResult;
import dev.ngb.tenant.billing.api.model.Subscription;
import dev.ngb.tenant.billing.api.model.SubscriptionDetail;
import dev.ngb.tenant.billing.api.model.UsageEvent;
import dev.ngb.tenant.billing.api.model.UsageEventRequest;
import dev.ngb.tenant.billing.api.model.Wallet;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The REST shape of {@code contracts/openapi/billing-tenant-system.yaml}. It is the only place that
 * knows a path, a query name or a header name; {@link FeignBillingClient} maps it onto the api
 * contracts. A null optional query or header is dropped by Feign, never sent empty.
 */
interface BillingFeignApi {
    @PostMapping(value = "/api/v1/customers", consumes = MediaType.APPLICATION_JSON_VALUE)
    Customer createCustomer(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody NewCustomer customer);

    @GetMapping("/api/v1/customers/{id}")
    Customer customer(@PathVariable("id") String id);

    @GetMapping("/api/v1/customers/by-external-user/{externalUserId}")
    Customer customerByExternalUser(
            @PathVariable("externalUserId") String externalUserId, @RequestParam("tenantSystemId") String tenantSystemId);

    @GetMapping("/api/v1/pricing-plans")
    List<PricingPlan> plans(@RequestParam(value = "status", required = false) String status);

    @GetMapping("/api/v1/pricing-plans/{id}")
    PricingPlan plan(@PathVariable("id") String id);

    @PostMapping(value = "/api/v1/pricing/estimates", consumes = MediaType.APPLICATION_JSON_VALUE)
    Estimate estimate(@RequestBody EstimateRequest request);

    @PostMapping(value = "/api/v1/pricing/reservations", consumes = MediaType.APPLICATION_JSON_VALUE)
    ReserveResult reserve(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody ReserveRequest request);

    @GetMapping("/api/v1/wallets")
    List<Wallet> wallets(@RequestParam("customerId") String customerId);

    @GetMapping("/api/v1/wallets/{id}")
    Wallet wallet(@PathVariable("id") String id);

    @PostMapping(value = "/api/v1/wallets", consumes = MediaType.APPLICATION_JSON_VALUE)
    Wallet openWallet(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody OpenWallet request);

    @PostMapping(value = "/api/v1/charges", consumes = MediaType.APPLICATION_JSON_VALUE)
    Charge charge(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody ChargeRequest request);

    @PostMapping(value = "/api/v1/reservations/{reservationId}/capture", consumes = MediaType.APPLICATION_JSON_VALUE)
    Charge capture(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable("reservationId") String reservationId,
            @RequestBody CaptureRequest request);

    @PostMapping("/api/v1/reservations/{reservationId}/release")
    Reservation release(@PathVariable("reservationId") String reservationId);

    @PostMapping(value = "/api/v1/pricing/usage-charges", consumes = MediaType.APPLICATION_JSON_VALUE)
    Rating chargeUsage(@RequestBody ChargeUsageRequest request);

    @GetMapping("/api/v1/pricing/usage-charges/{id}")
    Rating rating(@PathVariable("id") String id);

    @PostMapping(value = "/api/v1/usage-events", consumes = MediaType.APPLICATION_JSON_VALUE)
    RecordedUsage record(@RequestBody UsageEventRequest request);

    @GetMapping("/api/v1/usage-events")
    List<UsageEvent> history(
            @RequestParam("customerId") String customerId,
            @RequestParam(value = "metric", required = false) String metric,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam("limit") int limit);

    @PostMapping(value = "/api/v1/billing-sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    BillingSession createSession(@RequestBody SessionRequest request);

    @PostMapping("/api/v1/billing-sessions/{id}/revoke")
    void revokeSession(@PathVariable("id") String id);

    @GetMapping("/api/v1/payments")
    List<Payment> payments(
            @RequestParam(value = "walletId", required = false) String walletId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("limit") int limit);

    @GetMapping("/api/v1/payments/{id}")
    PaymentDetail payment(@PathVariable("id") String id);

    @PostMapping(value = "/api/v1/payments/top-ups", consumes = MediaType.APPLICATION_JSON_VALUE)
    Payment createTopUp(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody TopUpRequest request);

    @GetMapping("/api/v1/invoices")
    List<Invoice> invoices(
            @RequestParam(value = "customerId", required = false) String customerId,
            @RequestParam(value = "walletId", required = false) String walletId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("limit") int limit);

    @GetMapping("/api/v1/invoices/{id}")
    InvoiceDetail invoice(@PathVariable("id") String id);

    @PostMapping(value = "/api/v1/subscriptions", consumes = MediaType.APPLICATION_JSON_VALUE)
    SubscriptionDetail subscribe(
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody SubscribeRequest request);

    @GetMapping("/api/v1/subscriptions/{id}")
    SubscriptionDetail subscription(@PathVariable("id") String id);

    @PostMapping("/api/v1/subscriptions/{id}/cancel")
    SubscriptionDetail cancel(@PathVariable("id") String id);

    @GetMapping("/api/v1/subscriptions")
    List<Subscription> subscriptions(
            @RequestParam(value = "customerId", required = false) String customerId,
            @RequestParam(value = "walletId", required = false) String walletId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("limit") int limit);

    @PostMapping(value = "/api/v1/pricing-plans", consumes = MediaType.APPLICATION_JSON_VALUE)
    PricingPlan createPlan(@RequestBody CreatePlanRequest request);

    @GetMapping("/api/v1/webhook-endpoints")
    List<Endpoint> endpoints();

    @PostMapping(value = "/api/v1/webhook-endpoints", consumes = MediaType.APPLICATION_JSON_VALUE)
    EndpointWithSecret createEndpoint(@RequestBody CreateEndpointRequest request);

    record SessionRequest(String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds) {}

    record SubscribeRequest(String walletId, String planId) {}
}
