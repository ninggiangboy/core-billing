package dev.ngb.tenant.billing.adapter.client.http;

import dev.ngb.tenant.billing.api.client.BillingAdmin;
import dev.ngb.tenant.billing.api.client.BillingCharges;
import dev.ngb.tenant.billing.api.client.BillingCustomers;
import dev.ngb.tenant.billing.api.client.BillingInvoices;
import dev.ngb.tenant.billing.api.client.BillingPayments;
import dev.ngb.tenant.billing.api.client.BillingPricing;
import dev.ngb.tenant.billing.api.client.BillingSessions;
import dev.ngb.tenant.billing.api.client.BillingSubscriptions;
import dev.ngb.tenant.billing.api.client.BillingUsage;
import dev.ngb.tenant.billing.api.client.BillingWallets;
import dev.ngb.tenant.billing.api.client.BillingWebhooks;
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
import dev.ngb.tenant.shared.error.BadRequestException;
import dev.ngb.tenant.shared.error.ConflictException;
import dev.ngb.tenant.shared.error.ForbiddenException;
import dev.ngb.tenant.shared.error.NotFoundException;
import dev.ngb.tenant.shared.error.PaymentRequiredException;
import dev.ngb.tenant.shared.error.UnauthorizedException;
import dev.ngb.tenant.shared.error.UpstreamUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hand-written typed client over {@code contracts/openapi/billing-tenant-system.yaml}.
 *
 * <p>Every write carries the caller's Idempotency-Key, and a transport failure or 5xx is retried
 * with that same key, so Billing records the operation once.
 */
public class HttpBillingClient
        implements BillingCustomers,
                BillingPricing,
                BillingWallets,
                BillingCharges,
                BillingUsage,
                BillingSessions,
                BillingPayments,
                BillingInvoices,
                BillingSubscriptions,
                BillingAdmin,
                BillingWebhooks {

    private final HttpClient http;
    private final ObjectMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final HttpBillingSettings settings;
    private final String baseUrl;

    public HttpBillingClient(HttpBillingSettings settings) {
        this.settings = settings;
        this.baseUrl = stripTrailingSlash(settings.baseUrl());
        this.http = HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build();
    }

    @Override
    public Customer create(String idempotencyKey, NewCustomer customer) {
        return send(
                "POST",
                "/api/v1/customers",
                customer,
                Map.of("Idempotency-Key", idempotencyKey),
                new TypeReference<>() {});
    }

    @Override
    public Customer get(String id) {
        return send("GET", "/api/v1/customers/" + path(id), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public Customer byExternalUser(String externalUserId, String tenantSystemId) {
        String query = "?tenantSystemId=" + query(tenantSystemId);
        return send(
                "GET",
                "/api/v1/customers/by-external-user/" + path(externalUserId) + query,
                null,
                Map.of(),
                new TypeReference<>() {});
    }

    @Override
    public List<PricingPlan> plans(String status) {
        String query = status == null ? "" : "?status=" + query(status);
        return send("GET", "/api/v1/pricing-plans" + query, null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public PricingPlan plan(String id) {
        return send("GET", "/api/v1/pricing-plans/" + path(id), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public Estimate estimate(EstimateRequest request) {
        return send("POST", "/api/v1/pricing/estimates", request, Map.of(), new TypeReference<>() {});
    }

    @Override
    public ReserveResult reserve(String idempotencyKey, ReserveRequest request) {
        return send(
                "POST",
                "/api/v1/pricing/reservations",
                request,
                Map.of("Idempotency-Key", idempotencyKey),
                new TypeReference<>() {});
    }

    @Override
    public List<Wallet> wallets(String customerId) {
        return send(
                "GET",
                "/api/v1/wallets?customerId=" + query(customerId),
                null,
                Map.of(),
                new TypeReference<>() {});
    }

    @Override
    public Wallet wallet(String id) {
        return send("GET", "/api/v1/wallets/" + path(id), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public Wallet open(String idempotencyKey, OpenWallet request) {
        return send(
                "POST",
                "/api/v1/wallets",
                request,
                Map.of("Idempotency-Key", idempotencyKey),
                new TypeReference<>() {});
    }

    @Override
    public Charge charge(String idempotencyKey, ChargeRequest request) {
        return send(
                "POST",
                "/api/v1/charges",
                request,
                Map.of("Idempotency-Key", idempotencyKey),
                new TypeReference<>() {});
    }

    @Override
    public Charge capture(String idempotencyKey, String reservationId, CaptureRequest request) {
        return send(
                "POST",
                "/api/v1/reservations/" + path(reservationId) + "/capture",
                request,
                Map.of("Idempotency-Key", idempotencyKey),
                new TypeReference<>() {});
    }

    @Override
    public Reservation release(String reservationId) {
        return send(
                "POST",
                "/api/v1/reservations/" + path(reservationId) + "/release",
                null,
                Map.of(),
                new TypeReference<>() {});
    }

    @Override
    public Rating chargeUsage(ChargeUsageRequest request) {
        return send("POST", "/api/v1/pricing/usage-charges", request, Map.of(), new TypeReference<>() {});
    }

    @Override
    public Rating rating(String id) {
        return send(
                "GET", "/api/v1/pricing/usage-charges/" + path(id), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public RecordedUsage record(UsageEventRequest request) {
        return send("POST", "/api/v1/usage-events", request, Map.of(), new TypeReference<>() {});
    }

    @Override
    public List<UsageEvent> history(String customerId, String metric, Instant from, Instant to, int limit) {
        List<String> params = new ArrayList<>();
        params.add("customerId=" + query(customerId));
        if (metric != null) {
            params.add("metric=" + query(metric));
        }
        if (from != null) {
            params.add("from=" + query(from.toString()));
        }
        if (to != null) {
            params.add("to=" + query(to.toString()));
        }
        params.add("limit=" + limit);
        return send(
                "GET",
                "/api/v1/usage-events?" + String.join("&", params),
                null,
                Map.of(),
                new TypeReference<>() {});
    }

    @Override
    public BillingSession create(String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds) {
        return send(
                "POST",
                "/api/v1/billing-sessions",
                new SessionRequest(customerId, returnUrl, permissions, expiresInSeconds),
                Map.of(),
                new TypeReference<>() {});
    }

    @Override
    public void revoke(String id) {
        send("POST", "/api/v1/billing-sessions/" + path(id) + "/revoke", null, Map.of(), null);
    }

    @Override
    public List<Payment> payments(String walletId, String status, int limit) {
        List<String> params = new ArrayList<>();
        if (walletId != null) {
            params.add("walletId=" + query(walletId));
        }
        if (status != null) {
            params.add("status=" + query(status));
        }
        params.add("limit=" + limit);
        return send(
                "GET", "/api/v1/payments?" + String.join("&", params), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public PaymentDetail payment(String id) {
        return send("GET", "/api/v1/payments/" + path(id), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public Payment createTopUp(String idempotencyKey, TopUpRequest request) {
        return send(
                "POST",
                "/api/v1/payments/top-ups",
                request,
                Map.of("Idempotency-Key", idempotencyKey),
                new TypeReference<>() {});
    }

    @Override
    public List<Invoice> invoices(String customerId, String walletId, String status, int limit) {
        List<String> params = new ArrayList<>();
        if (customerId != null) {
            params.add("customerId=" + query(customerId));
        }
        if (walletId != null) {
            params.add("walletId=" + query(walletId));
        }
        if (status != null) {
            params.add("status=" + query(status));
        }
        params.add("limit=" + limit);
        return send(
                "GET", "/api/v1/invoices?" + String.join("&", params), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public InvoiceDetail invoice(String id) {
        return send("GET", "/api/v1/invoices/" + path(id), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public SubscriptionDetail subscribe(String idempotencyKey, String walletId, String planId) {
        return send(
                "POST",
                "/api/v1/subscriptions",
                new SubscribeRequest(walletId, planId),
                Map.of("Idempotency-Key", idempotencyKey),
                new TypeReference<>() {});
    }

    @Override
    public SubscriptionDetail subscription(String id) {
        return send("GET", "/api/v1/subscriptions/" + path(id), null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public SubscriptionDetail cancel(String id) {
        return send(
                "POST", "/api/v1/subscriptions/" + path(id) + "/cancel", null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public List<Subscription> subscriptions(String customerId, String walletId, String status, int limit) {
        List<String> params = new ArrayList<>();
        if (customerId != null) {
            params.add("customerId=" + query(customerId));
        }
        if (walletId != null) {
            params.add("walletId=" + query(walletId));
        }
        if (status != null) {
            params.add("status=" + query(status));
        }
        params.add("limit=" + limit);
        return send(
                "GET", "/api/v1/subscriptions?" + String.join("&", params), null, Map.of(), new TypeReference<>() {});
    }

    private record SubscribeRequest(String walletId, String planId) {}

    @Override
    public PricingPlan createPlan(CreatePlanRequest request) {
        return send("POST", "/api/v1/pricing-plans", request, Map.of(), new TypeReference<>() {});
    }

    @Override
    public List<Endpoint> endpoints() {
        return send("GET", "/api/v1/webhook-endpoints", null, Map.of(), new TypeReference<>() {});
    }

    @Override
    public EndpointWithSecret createEndpoint(CreateEndpointRequest request) {
        return send("POST", "/api/v1/webhook-endpoints", request, Map.of(), new TypeReference<>() {});
    }

    private record SessionRequest(
            String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds) {}

    private <T> T send(
            String method, String path, Object body, Map<String, String> headers, TypeReference<T> type) {
        HttpRequest request = build(method, path, body, headers);
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            try {
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                boolean retryable = status >= 500 || status == 429;
                if (retryable && attempt < settings.maxAttempts()) {
                    pause(attempt);
                    continue;
                }
                return read(response.statusCode(), response.body(), type);
            } catch (IOException e) {
                lastFailure = e;
                if (attempt < settings.maxAttempts()) {
                    pause(attempt);
                    continue;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UpstreamUnavailableException("the call to Billing was interrupted", e);
            }
        }
        throw new UpstreamUnavailableException("Billing could not be reached", lastFailure);
    }

    private HttpRequest build(String method, String path, Object body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(settings.readTimeout())
                .header("Accept", "application/json");
        if (!settings.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + settings.apiKey());
        }
        headers.forEach(builder::header);
        if (body == null) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(write(body)))
                .build();
    }

    private <T> T read(int status, byte[] body, TypeReference<T> type) {
        if (status >= 200 && status < 300) {
            if (type == null || body.length == 0) {
                return null;
            }
            try {
                return json.readValue(body, type);
            } catch (JacksonException e) {
                throw new UpstreamUnavailableException("Billing answered with a body this client cannot read", e);
            }
        }
        String detail = detail(body);
        switch (status) {
            case 401 -> throw new UnauthorizedException("Billing rejected the credential");
            case 402 -> throw new PaymentRequiredException(detail);
            case 403 -> throw new ForbiddenException("Billing refused this operation for the credential");
            case 404 -> throw new NotFoundException(detail);
            case 409 -> throw new ConflictException(detail);
            default -> {
                if (status >= 400 && status < 500) {
                    throw new BadRequestException(detail);
                }
                throw new UpstreamUnavailableException("Billing answered " + status + ": " + detail);
            }
        }
    }

    private String detail(byte[] body) {
        if (body.length == 0) {
            return "the request was refused";
        }
        try {
            JsonNode node = json.readTree(body);
            String detail = node.path("detail").asString("");
            return detail.isBlank() ? node.path("title").asString("the request was refused") : detail;
        } catch (JacksonException e) {
            return "the request was refused";
        }
    }

    private byte[] write(Object body) {
        try {
            return json.writeValueAsBytes(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("a request body could not be serialized", e);
        }
    }

    private void pause(int attempt) {
        long millis = settings.retryBackoff().toMillis() * attempt;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException("the retry wait was interrupted", e);
        }
    }

    private static String path(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
