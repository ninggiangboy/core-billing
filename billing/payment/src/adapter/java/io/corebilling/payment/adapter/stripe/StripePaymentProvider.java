package io.corebilling.payment.adapter.stripe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.PermissionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponse;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.RefundListParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionListParams;
import com.stripe.param.v2.core.AccountCreateParams;
import com.stripe.param.v2.core.AccountLinkCreateParams;
import com.stripe.param.v2.core.AccountRetrieveParams;
import io.corebilling.payment.application.InvalidSignatureException;
import io.corebilling.payment.application.PaymentProvider;
import io.corebilling.payment.application.ProviderRejectedException;
import io.corebilling.payment.application.ProviderUnavailableException;
import io.corebilling.payment.domain.StripeEnvironment;
import io.corebilling.sharedkernel.Money;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Stripe implementation of {@link PaymentProvider}. Stripe's types live in this package and nowhere else.
 *
 * <p>The API version is the one the SDK is pinned to ({@link Stripe#API_VERSION}); upgrading the SDK is how it
 * changes, and a test fails when that happens unnoticed. Every write carries an idempotency key. Payments are
 * created as Checkout Sessions (Stripe-hosted, so card data never touches Billing) with no
 * {@code payment_method_types}, which lets the Tenant's Dashboard settings decide which methods to offer. On a
 * connected account the calls carry the {@code Stripe-Account} header, so the charge is a Direct charge.
 */
public class StripePaymentProvider implements PaymentProvider {

    /** The API version Billing was written against; see the class comment. */
    public static final String PINNED_API_VERSION = "2026-09-30.endive";

    private static final int MAX_LISTED = 500;

    private final String apiBase;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final long toleranceSeconds;
    private final String integrationLabel;

    /**
     * @param apiBase Stripe's API root; null for the real one. Tests point it at a local fake.
     * @param toleranceSeconds how far a webhook's timestamp may be from now before it is rejected as a replay
     */
    public StripePaymentProvider(String apiBase, Duration connectTimeout, Duration readTimeout, long toleranceSeconds) {
        this.apiBase = apiBase;
        this.connectTimeoutMillis = (int) connectTimeout.toMillis();
        this.readTimeoutMillis = (int) readTimeout.toMillis();
        this.toleranceSeconds = toleranceSeconds;
        this.integrationLabel = "core-billing-" + randomLetters(8);
    }

    private StripeClient client(String secretKey) {
        StripeClient.StripeClientBuilder builder = StripeClient.builder()
                .setApiKey(secretKey)
                .setConnectTimeout(connectTimeoutMillis)
                .setReadTimeout(readTimeoutMillis)
                .setMaxNetworkRetries(2);
        if (apiBase != null) {
            builder.setApiBase(apiBase);
        }
        return builder.build();
    }

    private static RequestOptions options(String idempotencyKey, String connectedAccount) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder();
        if (idempotencyKey != null) {
            builder.setIdempotencyKey(idempotencyKey);
        }
        if (connectedAccount != null) {
            builder.setStripeAccount(connectedAccount);
        }
        return builder.build();
    }

    @Override
    public VerifiedAccount verifyKey(String secretKey) {
        try {
            StripeResponse response = client(secretKey).rawRequest(ApiResource.RequestMethod.GET, "/v1/account", null);
            JsonObject account = JsonParser.parseString(response.body()).getAsJsonObject();
            return new VerifiedAccount(account.get("id").getAsString(), environmentOf(secretKey));
        } catch (StripeException e) {
            throw translate(e);
        } catch (JsonParseException | IllegalStateException | NullPointerException e) {
            throw new ProviderRejectedException("Stripe's answer to the key check could not be understood");
        }
    }

    @Override
    public ProviderEvent verifyWebhook(String payload, String signatureHeader, String webhookSecret) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidSignatureException("the Stripe-Signature header is missing");
        }
        try {
            Webhook.constructEvent(payload, signatureHeader, webhookSecret, toleranceSeconds);
            return StripeSubjects.event(payload);
        } catch (SignatureVerificationException e) {
            throw new InvalidSignatureException("the signature does not match the body, or its timestamp is outside the tolerance");
        } catch (JsonParseException | IllegalStateException e) {
            throw new InvalidSignatureException("the body is not a Stripe event");
        }
    }

    @Override
    public ProviderEvent interpret(String payload) {
        return StripeSubjects.event(payload);
    }

    @Override
    public String createConnectedAccount(String platformSecretKey, String displayName, String contactEmail, String idempotencyKey) {
        // A SaaS platform: the Tenant runs its own business, so it is the merchant, pays Stripe's fees and
        // carries losses, with the full Stripe Dashboard, and charges are created directly on its account.
        AccountCreateParams.Builder params = AccountCreateParams.builder()
                .setDisplayName(displayName)
                .setDashboard(AccountCreateParams.Dashboard.FULL)
                .setDefaults(AccountCreateParams.Defaults.builder()
                        .setResponsibilities(AccountCreateParams.Defaults.Responsibilities.builder()
                                .setFeesCollector(AccountCreateParams.Defaults.Responsibilities.FeesCollector.STRIPE)
                                .setLossesCollector(AccountCreateParams.Defaults.Responsibilities.LossesCollector.STRIPE)
                                .build())
                        .build())
                .setConfiguration(AccountCreateParams.Configuration.builder()
                        .setMerchant(AccountCreateParams.Configuration.Merchant.builder()
                                .setCapabilities(AccountCreateParams.Configuration.Merchant.Capabilities.builder()
                                        .setCardPayments(AccountCreateParams.Configuration.Merchant.Capabilities.CardPayments.builder()
                                                .setRequested(true)
                                                .build())
                                        .build())
                                .build())
                        .build());
        if (contactEmail != null) {
            params.setContactEmail(contactEmail);
        }
        try {
            return client(platformSecretKey).v2().core().accounts().create(params.build(), options(idempotencyKey, null)).getId();
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public String createOnboardingLink(String platformSecretKey, String accountId, String returnUrl, String refreshUrl) {
        AccountLinkCreateParams.UseCase.AccountOnboarding onboarding = AccountLinkCreateParams.UseCase.AccountOnboarding.builder()
                .setReturnUrl(returnUrl)
                .setRefreshUrl(refreshUrl)
                .putExtraParam("configurations", List.of("merchant"))
                .build();
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setUseCase(AccountLinkCreateParams.UseCase.builder()
                        .setType(AccountLinkCreateParams.UseCase.Type.ACCOUNT_ONBOARDING)
                        .setAccountOnboarding(onboarding)
                        .build())
                .build();
        try {
            return client(platformSecretKey).v2().core().accountLinks().create(params, options(null, null)).getUrl();
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public AccountState retrieveAccount(String platformSecretKey, String accountId) {
        AccountRetrieveParams params = AccountRetrieveParams.builder().addInclude(AccountRetrieveParams.Include.CONFIGURATION__MERCHANT).build();
        try {
            var account = client(platformSecretKey).v2().core().accounts().retrieve(accountId, params, options(null, null));
            // The v2 capability status, not the deprecated charges_enabled flag.
            boolean active = account.getConfiguration() != null
                    && account.getConfiguration().getMerchant() != null
                    && account.getConfiguration().getMerchant().getCapabilities() != null
                    && account.getConfiguration().getMerchant().getCapabilities().getCardPayments() != null
                    && "active".equals(account.getConfiguration().getMerchant().getCapabilities().getCardPayments().getStatus());
            return new AccountState(active);
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public CreatedCheckout createCheckout(Access access, CheckoutRequest request, String idempotencyKey) {
        Money amount = request.amount();
        requireStripeAmount(amount);
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setIntegrationIdentifier(integrationLabel)
                .setClientReferenceId(request.paymentId().toString())
                .setSuccessUrl(request.successUrl())
                .setCancelUrl(request.cancelUrl())
                .putMetadata(StripeSubjects.PAYMENT_REF, request.paymentId().toString())
                .putMetadata(StripeSubjects.TENANT_REF, request.tenantId().toString())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .setDescription(request.description())
                        .putMetadata(StripeSubjects.PAYMENT_REF, request.paymentId().toString())
                        .putMetadata(StripeSubjects.TENANT_REF, request.tenantId().toString())
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(amount.currency().code().toLowerCase(java.util.Locale.ROOT))
                                .setUnitAmount(amount.minorUnits())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().setName(request.description()).build())
                                .build())
                        .build())
                .build();
        try {
            Session session = client(access.secretKey()).v1().checkout().sessions().create(params, options(idempotencyKey, access.connectedAccountId()));
            if (session.getUrl() == null) {
                throw new ProviderRejectedException("Stripe created the checkout without a hosted page");
            }
            return new CreatedCheckout(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public Intent retrievePayment(Access access, String paymentIntentId) {
        try {
            PaymentIntent intent = client(access.secretKey()).v1().paymentIntents().retrieve(paymentIntentId, options(null, access.connectedAccountId()));
            return (Intent) StripeSubjects.subject(intent.toJson());
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public RefundObject createRefund(Access access, RefundRequest request, String idempotencyKey) {
        requireStripeAmount(request.amount());
        RefundCreateParams.Builder params = RefundCreateParams.builder()
                .setPaymentIntent(request.paymentIntentId())
                .setAmount(request.amount().minorUnits())
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata(StripeSubjects.REFUND_REF, request.refundId().toString())
                .putMetadata(StripeSubjects.PAYMENT_REF, request.paymentId().toString());
        try {
            Refund refund = client(access.secretKey()).v1().refunds().create(params.build(), options(idempotencyKey, access.connectedAccountId()));
            return (RefundObject) StripeSubjects.subject(refund.toJson());
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public List<Checkout> recentCheckouts(Access access, Instant since) {
        SessionListParams params = SessionListParams.builder()
                .setCreated(SessionListParams.Created.builder().setGte(since.getEpochSecond()).build())
                .setLimit(100L)
                .build();
        try {
            List<Checkout> found = new ArrayList<>();
            for (Session session : client(access.secretKey()).v1().checkout().sessions().list(params, options(null, access.connectedAccountId()))
                    .autoPagingIterable()) {
                if (StripeSubjects.subject(session.toJson()) instanceof Checkout checkout) {
                    found.add(checkout);
                }
                if (found.size() >= MAX_LISTED) {
                    break;
                }
            }
            return found;
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public List<RefundObject> recentRefunds(Access access, Instant since) {
        RefundListParams params = RefundListParams.builder()
                .setCreated(RefundListParams.Created.builder().setGte(since.getEpochSecond()).build())
                .setLimit(100L)
                .build();
        try {
            List<RefundObject> found = new ArrayList<>();
            for (Refund refund : client(access.secretKey()).v1().refunds().list(params, options(null, access.connectedAccountId())).autoPagingIterable()) {
                if (StripeSubjects.subject(refund.toJson()) instanceof RefundObject object) {
                    found.add(object);
                }
                if (found.size() >= MAX_LISTED) {
                    break;
                }
            }
            return found;
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    /** Three-decimal currencies must be sent in whole hundredths, which Stripe rounds otherwise. */
    private static void requireStripeAmount(Money amount) {
        if (amount.currency().exponent() == 3 && amount.minorUnits() % 10 != 0) {
            throw new IllegalArgumentException("amounts in " + amount.currency() + " must be a multiple of 10 minor units for Stripe");
        }
    }

    private static StripeEnvironment environmentOf(String secretKey) {
        Objects.requireNonNull(secretKey, "secretKey");
        return secretKey.contains("_test_") ? StripeEnvironment.TEST : StripeEnvironment.LIVE;
    }

    /**
     * Turns Stripe's failures into the two the application understands. Messages are chosen here, never copied
     * from Stripe's exception text, because that text can echo parts of a credential.
     */
    private static RuntimeException translate(StripeException e) {
        if (e instanceof AuthenticationException || e instanceof PermissionException) {
            return new ProviderRejectedException("Stripe rejected the credentials");
        }
        if (e instanceof ApiConnectionException || e instanceof ApiException || e instanceof RateLimitException) {
            return new ProviderUnavailableException("Stripe could not be reached or is busy", e);
        }
        Integer status = e.getStatusCode();
        if (status != null && status >= 500) {
            return new ProviderUnavailableException("Stripe answered with a server error", e);
        }
        String code = e.getCode();
        return new ProviderRejectedException("Stripe rejected the request" + (code == null ? "" : " (" + code + ")"));
    }

    private static String randomLetters(int count) {
        SecureRandom random = new SecureRandom();
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < count; i++) {
            letters.append((char) ('a' + random.nextInt(26)));
        }
        return letters.toString();
    }

    /** Exposed for the test that notices an SDK upgrade. */
    public static String sdkApiVersion() {
        return Stripe.API_VERSION;
    }
}
