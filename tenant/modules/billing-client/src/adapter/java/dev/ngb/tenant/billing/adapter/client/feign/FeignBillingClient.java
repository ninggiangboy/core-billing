package dev.ngb.tenant.billing.adapter.client.feign;

import dev.ngb.tenant.billing.api.client.BillingAdmin;
import dev.ngb.tenant.billing.api.client.BillingAdmin.CreatePlanRequest;
import dev.ngb.tenant.billing.api.client.BillingCharges;
import dev.ngb.tenant.billing.api.client.BillingCharges.CaptureRequest;
import dev.ngb.tenant.billing.api.client.BillingCharges.ChargeRequest;
import dev.ngb.tenant.billing.api.client.BillingCustomers;
import dev.ngb.tenant.billing.api.client.BillingCustomers.NewCustomer;
import dev.ngb.tenant.billing.api.client.BillingInvoices;
import dev.ngb.tenant.billing.api.client.BillingPayments;
import dev.ngb.tenant.billing.api.client.BillingPayments.TopUpRequest;
import dev.ngb.tenant.billing.api.client.BillingPricing;
import dev.ngb.tenant.billing.api.client.BillingPricing.EstimateRequest;
import dev.ngb.tenant.billing.api.client.BillingPricing.ReserveRequest;
import dev.ngb.tenant.billing.api.client.BillingSessions;
import dev.ngb.tenant.billing.api.client.BillingSubscriptions;
import dev.ngb.tenant.billing.api.client.BillingUsage;
import dev.ngb.tenant.billing.api.client.BillingWallets;
import dev.ngb.tenant.billing.api.client.BillingWallets.OpenWallet;
import dev.ngb.tenant.billing.api.client.BillingWebhooks;
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
import dev.ngb.tenant.shared.error.UpstreamUnavailableException;
import feign.RetryableException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The api contracts over Feign. Each method delegates the shape to {@link BillingFeignApi} and turns
 * an exhausted retry into {@link UpstreamUnavailableException}, so callers only ever see domain
 * exceptions.
 */
public class FeignBillingClient
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

    private final BillingFeignApi api;

    FeignBillingClient(BillingFeignApi api) {
        this.api = api;
    }

    @Override
    public Customer create(String idempotencyKey, NewCustomer customer) {
        requireKey(idempotencyKey);
        return call(() -> api.createCustomer(idempotencyKey, customer));
    }

    @Override
    public Customer get(String id) {
        return call(() -> api.customer(id));
    }

    @Override
    public Customer byExternalUser(String externalUserId, String tenantSystemId) {
        return call(() -> api.customerByExternalUser(externalUserId, tenantSystemId));
    }

    @Override
    public List<PricingPlan> plans(String status) {
        return call(() -> api.plans(status));
    }

    @Override
    public PricingPlan plan(String id) {
        return call(() -> api.plan(id));
    }

    @Override
    public Estimate estimate(EstimateRequest request) {
        return call(() -> api.estimate(request));
    }

    @Override
    public ReserveResult reserve(String idempotencyKey, ReserveRequest request) {
        requireKey(idempotencyKey);
        return call(() -> api.reserve(idempotencyKey, request));
    }

    @Override
    public List<Wallet> wallets(String customerId) {
        return call(() -> api.wallets(customerId));
    }

    @Override
    public Wallet wallet(String id) {
        return call(() -> api.wallet(id));
    }

    @Override
    public Wallet open(String idempotencyKey, OpenWallet request) {
        requireKey(idempotencyKey);
        return call(() -> api.openWallet(idempotencyKey, request));
    }

    @Override
    public Charge charge(String idempotencyKey, ChargeRequest request) {
        requireKey(idempotencyKey);
        return call(() -> api.charge(idempotencyKey, request));
    }

    @Override
    public Charge capture(String idempotencyKey, String reservationId, CaptureRequest request) {
        requireKey(idempotencyKey);
        return call(() -> api.capture(idempotencyKey, reservationId, request));
    }

    @Override
    public Reservation release(String reservationId) {
        return call(() -> api.release(reservationId));
    }

    @Override
    public Rating chargeUsage(ChargeUsageRequest request) {
        return call(() -> api.chargeUsage(request));
    }

    @Override
    public Rating rating(String id) {
        return call(() -> api.rating(id));
    }

    @Override
    public RecordedUsage record(UsageEventRequest request) {
        return call(() -> api.record(request));
    }

    @Override
    public List<UsageEvent> history(String customerId, String metric, Instant from, Instant to, int limit) {
        return call(() -> api.history(customerId, metric, instant(from), instant(to), limit));
    }

    @Override
    public BillingSession create(String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds) {
        return call(() -> api.createSession(
                new BillingFeignApi.SessionRequest(customerId, returnUrl, permissions, expiresInSeconds)));
    }

    @Override
    public void revoke(String id) {
        run(() -> api.revokeSession(id));
    }

    @Override
    public List<Payment> payments(String walletId, String status, int limit) {
        return call(() -> api.payments(walletId, status, limit));
    }

    @Override
    public PaymentDetail payment(String id) {
        return call(() -> api.payment(id));
    }

    @Override
    public Payment createTopUp(String idempotencyKey, TopUpRequest request) {
        requireKey(idempotencyKey);
        return call(() -> api.createTopUp(idempotencyKey, request));
    }

    @Override
    public List<Invoice> invoices(String customerId, String walletId, String status, int limit) {
        return call(() -> api.invoices(customerId, walletId, status, limit));
    }

    @Override
    public InvoiceDetail invoice(String id) {
        return call(() -> api.invoice(id));
    }

    @Override
    public SubscriptionDetail subscribe(String idempotencyKey, String walletId, String planId) {
        requireKey(idempotencyKey);
        return call(() -> api.subscribe(idempotencyKey, new BillingFeignApi.SubscribeRequest(walletId, planId)));
    }

    @Override
    public SubscriptionDetail subscription(String id) {
        return call(() -> api.subscription(id));
    }

    @Override
    public SubscriptionDetail cancel(String id) {
        return call(() -> api.cancel(id));
    }

    @Override
    public List<Subscription> subscriptions(String customerId, String walletId, String status, int limit) {
        return call(() -> api.subscriptions(customerId, walletId, status, limit));
    }

    @Override
    public PricingPlan createPlan(CreatePlanRequest request) {
        return call(() -> api.createPlan(request));
    }

    @Override
    public List<Endpoint> endpoints() {
        return call(api::endpoints);
    }

    @Override
    public EndpointWithSecret createEndpoint(CreateEndpointRequest request) {
        return call(() -> api.createEndpoint(request));
    }

    private <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (RetryableException e) {
            throw new UpstreamUnavailableException("Billing could not be reached", e);
        }
    }

    private void run(Runnable call) {
        try {
            call.run();
        } catch (RetryableException e) {
            throw new UpstreamUnavailableException("Billing could not be reached", e);
        }
    }

    /** A write retried without its key could be recorded twice, so a missing key fails before any request. */
    private static void requireKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "a write to Billing needs an Idempotency-Key");
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }
}
