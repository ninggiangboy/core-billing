package dev.ngb.sampletenant.support;

import dev.ngb.sampletenant.billing.api.client.BillingAdmin;
import dev.ngb.sampletenant.billing.api.client.BillingCharges;
import dev.ngb.sampletenant.billing.api.client.BillingCustomers;
import dev.ngb.sampletenant.billing.api.client.BillingInvoices;
import dev.ngb.sampletenant.billing.api.client.BillingPayments;
import dev.ngb.sampletenant.billing.api.client.BillingPricing;
import dev.ngb.sampletenant.billing.api.client.BillingSessions;
import dev.ngb.sampletenant.billing.api.client.BillingSubscriptions;
import dev.ngb.sampletenant.billing.api.client.BillingUsage;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.billing.api.client.BillingWebhooks;
import dev.ngb.sampletenant.billing.api.model.Amount;
import dev.ngb.sampletenant.billing.api.model.BillingSession;
import dev.ngb.sampletenant.billing.api.model.Charge;
import dev.ngb.sampletenant.billing.api.model.ChargeUsageRequest;
import dev.ngb.sampletenant.billing.api.model.Customer;
import dev.ngb.sampletenant.billing.api.model.Estimate;
import dev.ngb.sampletenant.billing.api.model.Invoice;
import dev.ngb.sampletenant.billing.api.model.InvoiceDetail;
import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.billing.api.model.PaymentDetail;
import dev.ngb.sampletenant.billing.api.model.PricingPlan;
import dev.ngb.sampletenant.billing.api.model.Rating;
import dev.ngb.sampletenant.billing.api.model.RecordedUsage;
import dev.ngb.sampletenant.billing.api.model.Reservation;
import dev.ngb.sampletenant.billing.api.model.ReserveResult;
import dev.ngb.sampletenant.billing.api.model.Subscription;
import dev.ngb.sampletenant.billing.api.model.SubscriptionDetail;
import dev.ngb.sampletenant.billing.api.model.UsageEvent;
import dev.ngb.sampletenant.billing.api.model.UsageEventRequest;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.shared.error.PaymentRequiredException;
import dev.ngb.sampletenant.shared.error.UpstreamUnavailableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic in-memory stand-in for every Billing call the tenant makes. */
public final class FakeBilling implements
        BillingAdmin, BillingCharges, BillingCustomers, BillingInvoices, BillingPayments, BillingPricing,
        BillingSessions, BillingSubscriptions, BillingUsage, BillingWallets, BillingWebhooks {

    public static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    public static final String CURRENCY = "USD";

    private final AtomicInteger customers = new AtomicInteger();
    private final Map<String, Customer> byExternalUser = new LinkedHashMap<>();
    private final List<String> topUpKeys = new ArrayList<>();
    private final List<String> chargedReservations = new ArrayList<>();
    private boolean customersUnavailable;
    private boolean refuseReservation;
    private boolean chargeFails;

    public void customersUnavailable() {
        customersUnavailable = true;
    }

    public void customersRecover() {
        customersUnavailable = false;
    }

    public void refuseReservation() {
        refuseReservation = true;
    }

    public void acceptReservation() {
        refuseReservation = false;
    }

    public void chargeFails() {
        chargeFails = true;
    }

    public List<String> topUpKeys() {
        return topUpKeys;
    }

    public List<String> chargedReservations() {
        return chargedReservations;
    }

    // Customers

    @Override
    public Customer create(String idempotencyKey, NewCustomer customer) {
        if (customersUnavailable) {
            throw new UpstreamUnavailableException("billing is down");
        }
        return byExternalUser.computeIfAbsent(
                customer.externalUserId(),
                ignored -> new Customer("cus_" + customers.incrementAndGet(), customer.displayName(), customer.email(), "ACTIVE", NOW));
    }

    @Override
    public Customer get(String id) {
        return new Customer(id, "Ada", "ada@example.com", "ACTIVE", NOW);
    }

    @Override
    public Customer byExternalUser(String externalUserId, String tenantSystemId) {
        return create("lookup:" + externalUserId, new NewCustomer(tenantSystemId, externalUserId, "Ada", "ada@example.com"));
    }

    // Pricing

    @Override
    public List<PricingPlan> plans(String status) {
        return List.of();
    }

    @Override
    public PricingPlan createPlan(CreatePlanRequest request) {
        return new PricingPlan("bplan_1", request.code(), request.name(), "TIME_BASED", CURRENCY, "ACTIVE", NOW, null, List.of());
    }

    @Override
    public PricingPlan plan(String id) {
        return new PricingPlan(id, "compute-standard", "Compute", "TIME_BASED", CURRENCY, "ACTIVE", NOW, null, List.of());
    }

    @Override
    public Estimate estimate(EstimateRequest request) {
        return new Estimate(request.planId(), "compute-standard", 1, new Amount(1000, CURRENCY), "10.00", "0", List.of());
    }

    @Override
    public ReserveResult reserve(String idempotencyKey, ReserveRequest request) {
        if (refuseReservation) {
            throw new PaymentRequiredException("The wallet cannot cover this amount.");
        }
        Estimate estimate = new Estimate("bplan_1", "compute-standard", 1, new Amount(request.amount(), request.currency()), "10.00", "0", List.of());
        Reservation reservation = new Reservation(
                "res_1", request.walletId(), "HELD", new Amount(request.amount(), request.currency()), null,
                NOW.plusSeconds(900), request.reference(), NOW, null, false);
        return new ReserveResult(estimate, reservation);
    }

    @Override
    public Charge charge(String idempotencyKey, ChargeRequest request) {
        return chargeResult("chg_1", request.amount(), request.currency());
    }

    @Override
    public Charge capture(String idempotencyKey, String reservationId, CaptureRequest request) {
        chargedReservations.add(reservationId);
        return chargeResult("chg_2", request.amount(), request.currency());
    }

    private Charge chargeResult(String id, long amount, String currency) {
        if (chargeFails) {
            return new Charge(id, "FAILED", "insufficient_funds", new Amount(amount, currency), null, "res_1", null, "ORD-1");
        }
        return new Charge(id, "SUCCEEDED", null, new Amount(amount, currency), "led_1", "res_1", null, "ORD-1");
    }

    @Override
    public Reservation release(String reservationId) {
        return new Reservation(
                reservationId, "wal_1", "RELEASED", new Amount(0, CURRENCY), null, NOW, "ORD-1", NOW, NOW, false);
    }

    @Override
    public Rating chargeUsage(ChargeUsageRequest request) {
        return new Rating("CHARGED", "rat_1", request.planId(), 1, new Amount(100, CURRENCY), "chg_3", null, 1,
                NOW.minusSeconds(86_400), NOW, List.of(), false);
    }

    @Override
    public Rating rating(String id) {
        return chargeUsage(new ChargeUsageRequest("cus_1", "wal_1", "bplan_1", null, NOW, NOW));
    }

    // Wallets

    @Override
    public List<Wallet> wallets(String customerId) {
        return List.of(walletOf("wal_" + customerId, customerId, CURRENCY, "PREPAID", null));
    }

    @Override
    public Wallet wallet(String id) {
        return walletOf(id, "cus_1", CURRENCY, "PREPAID", null);
    }

    @Override
    public Wallet open(String idempotencyKey, OpenWallet request) {
        return walletOf("wal_" + request.customerId(), request.customerId(), request.currency(), request.mode(), request.creditLimit());
    }

    private static Wallet walletOf(String id, String customerId, String currency, String mode, Long creditLimit) {
        return new Wallet(
                id, customerId, currency, mode,
                new Amount(5000, currency), new Amount(0, currency), new Amount(5000, currency),
                creditLimit == null ? new Amount(0, currency) : new Amount(creditLimit, currency),
                new Amount(0, currency), 0L, NOW);
    }

    // Sessions

    @Override
    public BillingSession create(String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds) {
        return new BillingSession("ses_1", customerId, "https://tenant-a.example/portal#token", permissions, NOW.plusSeconds(3600));
    }

    @Override
    public void revoke(String id) {}

    // Invoices and payments

    @Override
    public List<Invoice> invoices(String customerId, String walletId, String status, int limit) {
        return List.of(new Invoice(
                "inv_1", "INV-000001", customerId, "wal_" + customerId, "OPEN", "CHARGE", new Amount(1000, CURRENCY),
                NOW, NOW, null, null, NOW, NOW, null, null));
    }

    @Override
    public InvoiceDetail invoice(String id) {
        return new InvoiceDetail(invoices("cus_1", null, null, 50).get(0), List.of(), List.of());
    }

    @Override
    public List<Payment> payments(String walletId, String status, int limit) {
        return List.of(
                new Payment("pay_1", walletId, "cus_1", "SUCCEEDED", new Amount(500, CURRENCY), null, null, null, "led_1", NOW, NOW, NOW),
                new Payment("pay_2", walletId, "cus_1", "FAILED", new Amount(500, CURRENCY), null, null, "declined", null, NOW, NOW, null));
    }

    @Override
    public PaymentDetail payment(String id) {
        return new PaymentDetail(payments("wal_1", null, 50).get(0), List.of(), List.of());
    }

    @Override
    public Payment createTopUp(String idempotencyKey, TopUpRequest request) {
        topUpKeys.add(idempotencyKey);
        return new Payment(
                "pay_1", request.walletId(), "cus_1", "PENDING", new Amount(request.amount(), request.currency()),
                null, "https://checkout.stripe.test/1", null, null, NOW, NOW, null);
    }

    // Subscriptions

    @Override
    public SubscriptionDetail subscribe(String idempotencyKey, String walletId, String planId) {
        return subscriptionDetail();
    }

    @Override
    public SubscriptionDetail subscription(String id) {
        return subscriptionDetail();
    }

    @Override
    public SubscriptionDetail cancel(String id) {
        return subscriptionDetail();
    }

    @Override
    public List<Subscription> subscriptions(String customerId, String walletId, String status, int limit) {
        return List.of(subscriptionDetail().subscription());
    }

    private static SubscriptionDetail subscriptionDetail() {
        return new SubscriptionDetail(
                new Subscription("sub_1", "cus_1", "wal_1", "bplan_1", "managed-service-standard", "MONTHLY",
                        "ACTIVE", NOW, 1, NOW, NOW.plusSeconds(2_592_000), false, null, NOW.plusSeconds(2_592_000), 0, NOW),
                List.of());
    }

    // Usage

    @Override
    public RecordedUsage record(UsageEventRequest request) {
        return new RecordedUsage(new UsageEvent(
                "use_1", request.customerId(), request.metric(), request.quantity(), request.unit(),
                request.occurredAt(), NOW, request.idempotencyKey(), "sys_1", request.resourceRef(), request.sourceEventId()), false);
    }

    @Override
    public List<UsageEvent> history(String customerId, String metric, Instant from, Instant to, int limit) {
        return List.of();
    }

    // Webhooks

    @Override
    public List<Endpoint> endpoints() {
        return List.of();
    }

    @Override
    public EndpointWithSecret createEndpoint(CreateEndpointRequest request) {
        return new EndpointWithSecret(
                new Endpoint("whe_1", request.url(), request.description(), request.eventTypes(), "ACTIVE", "key_1", NOW, NOW, NOW),
                "whsec_test");
    }
}
