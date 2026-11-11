package dev.ngb.core_billing.payment.adapter.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.stripe.exception.ProviderRejectedException;
import dev.ngb.core_billing.payment.application.stripe.exception.ProviderUnavailableException;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.refund.RefundId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEnvironment;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.ngb.core_billing.money.domain.wallet.Wallet;

/**
 * What the adapter actually puts on the wire, checked against a local stand-in for Stripe: the credentials and
 * account header, the idempotency key on every write, the pinned API version, the shape of the Checkout Session
 * and the way failures are translated. No real Stripe account is involved.
 */
class StripeAdapterWireTest {

    private record Seen(String method, String path, String query, java.util.Map<String, String> headers, String body) {
        String decodedBody() {
            return URLDecoder.decode(body, StandardCharsets.UTF_8);
        }
    }

    private HttpServer server;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private volatile Function<Seen, String[]> responder = request -> new String[] {"200", "{}"};
    private StripePaymentProvider provider;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Seen request = read(exchange);
            seen.add(request);
            String[] response = responder.apply(request);
            byte[] bytes = response[1].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(Integer.parseInt(response[0]), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        provider = new StripePaymentProvider("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofSeconds(5), 300);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static Seen read(HttpExchange exchange) throws IOException {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(java.util.Locale.ROOT), v.get(0)));
        return new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery(), headers,
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static final PaymentId PAYMENT = new PaymentId(UUID.randomUUID());
    private static final Money TWENTY_FIVE = Money.of(2500, Currency.of("USD"));

    private PaymentProvider.CheckoutRequest checkoutRequest() {
        return new PaymentProvider.CheckoutRequest(PAYMENT, new TenantId(UUID.randomUUID()), TWENTY_FIVE, "Wallet top-up",
                "https://app.example/done?p=" + PAYMENT, "https://app.example/cancel?p=" + PAYMENT);
    }

    @Test
    void aDirectCheckoutCarriesTheTenantsKeyAnIdempotencyKeyAndOurReference() {
        responder = r -> new String[] {"200", """
                {
                  "id": "cs_test_1",
                  "object": "checkout.session",
                  "url": "https://checkout.stripe.com/c/pay/cs_test_1"
                }
                """};

        PaymentProvider.CreatedCheckout created = provider.createCheckout(new PaymentProvider.Access("sk_test_tenant_key_123456", null), checkoutRequest(),
                "topup-" + PAYMENT);

        assertThat(created.sessionId()).isEqualTo("cs_test_1");
        assertThat(created.url()).startsWith("https://checkout.stripe.com/");
        Seen request = seen.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/checkout/sessions");
        assertThat(request.headers()).containsEntry("authorization", "Bearer sk_test_tenant_key_123456");
        assertThat(request.headers()).containsEntry("idempotency-key", "topup-" + PAYMENT);
        assertThat(request.headers()).containsEntry("stripe-version", StripePaymentProvider.PINNED_API_VERSION);
        assertThat(request.headers()).doesNotContainKey("stripe-account");
        String body = request.decodedBody();
        assertThat(body).contains("mode=payment");
        assertThat(body).contains("line_items[0][price_data][unit_amount]=2500");
        assertThat(body).contains("line_items[0][price_data][currency]=usd");
        assertThat(body).contains("metadata[billing_payment_id]=" + PAYMENT);
        assertThat(body).contains("payment_intent_data[metadata][billing_payment_id]=" + PAYMENT);
        assertThat(body).contains("client_reference_id=" + PAYMENT);
        assertThat(body).containsPattern("integration_identifier=core-billing-[a-z]{8}(&|$)");
        assertThat(body).as("dynamic payment methods: the Tenant's Dashboard settings decide").doesNotContain("payment_method_types");
    }

    @Test
    void aConnectedAccountChargeIsADirectChargeOnThatAccount() {
        responder = r -> new String[] {"200", """
                {
                  "id": "cs_test_2",
                  "object": "checkout.session",
                  "url": "https://checkout.stripe.com/c/pay/cs_test_2"
                }
                """};

        provider.createCheckout(new PaymentProvider.Access("sk_test_platform_key_123456", "acct_connected_1"), checkoutRequest(), "topup-x");

        assertThat(seen.get(0).headers()).containsEntry("stripe-account", "acct_connected_1");
        assertThat(seen.get(0).headers()).containsEntry("authorization", "Bearer sk_test_platform_key_123456");
    }

    @Test
    void aRefundNamesThePaymentIntentAndCarriesItsOwnReference() {
        RefundId refund = new RefundId(UUID.randomUUID());
        responder = r -> new String[] {"200", """
                {
                  "id": "re_1",
                  "object": "refund",
                  "status": "pending",
                  "amount": 1000,
                  "currency": "usd",
                  "payment_intent": "pi_1",
                  "metadata": {"billing_refund_id": "%s"}
                }
                """.formatted(refund)};

        PaymentProvider.RefundObject created = provider.createRefund(new PaymentProvider.Access("sk_test_k_1234567890", "acct_1"),
                new PaymentProvider.RefundRequest(refund, PAYMENT, "pi_1", Money.of(1000, Currency.of("USD")), "duplicate"), "refund-" + refund);

        assertThat(created.refundId()).isEqualTo("re_1");
        assertThat(created.refundRef()).isEqualTo(refund.toString());
        assertThat(created.state()).isEqualTo(PaymentProvider.RefundState.PENDING);
        Seen request = seen.get(0);
        assertThat(request.path()).isEqualTo("/v1/refunds");
        assertThat(request.headers()).containsEntry("idempotency-key", "refund-" + refund);
        assertThat(request.headers()).containsEntry("stripe-account", "acct_1");
        assertThat(request.decodedBody()).contains("payment_intent=pi_1").contains("amount=1000").contains("metadata[billing_refund_id]=" + refund);
    }

    @Test
    void verifyingAKeyReadsTheAccountAndReportsTheEnvironment() {
        responder = r -> new String[] {"200", """
                {
                  "id": "acct_123",
                  "object": "account"
                }
                """};

        PaymentProvider.VerifiedAccount account = provider.verifyKey("sk_test_abcdefghijklmnop");

        assertThat(account.accountId()).isEqualTo("acct_123");
        assertThat(account.environment()).isEqualTo(StripeEnvironment.TEST);
        assertThat(seen.get(0).method()).isEqualTo("GET");
        assertThat(seen.get(0).path()).isEqualTo("/v1/account");
        assertThat(provider.verifyKey("sk_live_abcdefghijklmnop").environment()).isEqualTo(StripeEnvironment.LIVE);
    }

    @Test
    void aConnectedAccountIsCreatedForTheSaasModel() {
        responder = r -> new String[] {"200", """
                {
                  "id": "acct_new",
                  "object": "v2.core.account"
                }
                """};

        String id = provider.createConnectedAccount("sk_test_platform_1234567890", "Acme Ltd", "owner@acme.test", "connect-1");

        assertThat(id).isEqualTo("acct_new");
        Seen request = seen.get(0);
        assertThat(request.path()).isEqualTo("/v2/core/accounts");
        assertThat(request.headers()).containsEntry("idempotency-key", "connect-1");
        assertThat(request.body()).contains("\"dashboard\":\"full\"").contains("\"fees_collector\":\"stripe\"").contains("\"losses_collector\":\"stripe\"")
                .contains("\"card_payments\":{\"requested\":true}").contains("\"display_name\":\"Acme Ltd\"");
    }

    @Test
    void anOnboardingLinkAsksForTheMerchantConfiguration() {
        responder = r -> new String[] {"200", """
                {
                  "url": "https://connect.stripe.com/setup/e/acct_1/abc",
                  "object": "v2.core.account_link"
                }
                """};

        String url = provider.createOnboardingLink("sk_test_platform_1234567890", "acct_1", "https://app.example/return", "https://app.example/refresh");

        assertThat(url).startsWith("https://connect.stripe.com/");
        Seen request = seen.get(0);
        assertThat(request.path()).isEqualTo("/v2/core/account_links");
        assertThat(request.body()).contains("\"account\":\"acct_1\"").contains("\"type\":\"account_onboarding\"").contains("\"configurations\":[\"merchant\"]")
                .contains("\"return_url\":\"https://app.example/return\"").contains("\"refresh_url\":\"https://app.example/refresh\"");
    }

    @Test
    void cardPaymentsAreActiveOnlyWhenTheV2CapabilityStatusSaysSo() {
        responder = r -> new String[] {"200", """
                {
                  "id": "acct_1",
                  "object": "v2.core.account",
                  "configuration": {
                    "merchant": {
                      "capabilities": {
                        "card_payments": {"status": "active"}
                      }
                    }
                  }
                }
                """};
        assertThat(provider.retrieveAccount("sk_test_platform_1234567890", "acct_1").cardPaymentsActive()).isTrue();
        assertThat(seen.get(0).query()).contains("configuration.merchant");

        responder = r -> new String[] {"200", """
                {
                  "id": "acct_1",
                  "object": "v2.core.account",
                  "configuration": {
                    "merchant": {
                      "capabilities": {
                        "card_payments": {"status": "restricted"}
                      }
                    }
                  }
                }
                """};
        assertThat(provider.retrieveAccount("sk_test_platform_1234567890", "acct_1").cardPaymentsActive()).isFalse();

        responder = r -> new String[] {"200", """
                {
                  "id": "acct_1",
                  "object": "v2.core.account"
                }
                """};
        assertThat(provider.retrieveAccount("sk_test_platform_1234567890", "acct_1").cardPaymentsActive()).isFalse();
    }

    @Test
    void recentCheckoutsAndRefundsAreListedFromTheGivenTime() {
        responder = r -> r.path().endsWith("/refunds")
                ? new String[] {"200", """
                        {
                          "object": "list",
                          "has_more": false,
                          "data": [
                            {
                              "id": "re_9",
                              "object": "refund",
                              "status": "succeeded",
                              "amount": 5,
                              "currency": "usd"
                            }
                          ]
                        }
                        """}
                : new String[] {"200", """
                        {
                          "object": "list",
                          "has_more": false,
                          "data": [
                            {
                              "id": "cs_9",
                              "object": "checkout.session",
                              "status": "complete",
                              "payment_status": "paid",
                              "amount_total": 7,
                              "currency": "usd",
                              "metadata": {"billing_payment_id": "%s"}
                            }
                          ]
                        }
                        """.formatted(PAYMENT)};
        Instant since = Instant.parse("2026-11-07T00:00:00Z");
        PaymentProvider.Access access = new PaymentProvider.Access("sk_test_k_1234567890", null);

        List<PaymentProvider.Checkout> checkouts = provider.recentCheckouts(access, since);
        List<PaymentProvider.RefundObject> refunds = provider.recentRefunds(access, since);

        assertThat(checkouts).extracting(PaymentProvider.Checkout::sessionId).containsExactly("cs_9");
        assertThat(checkouts.get(0).paymentRef()).isEqualTo(PAYMENT.toString());
        assertThat(refunds).extracting(PaymentProvider.RefundObject::refundId).containsExactly("re_9");
        assertThat(java.net.URLDecoder.decode(seen.get(0).query(), StandardCharsets.UTF_8)).contains("created[gte]=" + since.getEpochSecond());
    }

    @Test
    void anAuthenticationFailureIsRejectedWithoutEchoingAnythingFromStripe() {
        responder = r -> new String[] {"401", """
                {
                  "error": {
                    "type": "invalid_request_error",
                    "message": "Invalid API Key provided: sk_test_****1234"
                  }
                }
                """};

        assertThatThrownBy(() -> provider.verifyKey("sk_test_abcdefghijklmnop"))
                .isInstanceOf(ProviderRejectedException.class)
                .satisfies(e -> assertThat(e.getMessage()).isEqualTo("Stripe rejected the credentials"));
    }

    @Test
    void aRejectedRequestKeepsOnlyStripesErrorCode() {
        responder = r -> new String[] {"400", """
                {
                  "error": {
                    "type": "invalid_request_error",
                    "code": "amount_too_small",
                    "message": "Amount must be at least 50 cents"
                  }
                }
                """};

        assertThatThrownBy(() -> provider.createCheckout(new PaymentProvider.Access("sk_test_k_1234567890", null), checkoutRequest(), "k"))
                .isInstanceOf(ProviderRejectedException.class)
                .satisfies(e -> assertThat(e.getMessage()).isEqualTo("Stripe rejected the request (amount_too_small)"));
    }

    @Test
    void aServerErrorMeansTheOutcomeIsUnknownAndTheCallMayBeRepeated() {
        responder = r -> new String[] {"500", """
                {
                  "error": {
                    "type": "api_error",
                    "message": "boom"
                  }
                }
                """};

        assertThatThrownBy(() -> provider.createCheckout(new PaymentProvider.Access("sk_test_k_1234567890", null), checkoutRequest(), "k"))
                .isInstanceOf(ProviderUnavailableException.class);
        // The SDK retried with the same idempotency key before giving up.
        List<String> keys = new ArrayList<>();
        seen.forEach(s -> keys.add(s.headers().get("idempotency-key")));
        assertThat(keys).hasSizeGreaterThanOrEqualTo(2).containsOnly("k");
    }

    @Test
    void anUnreachableStripeIsUnavailable() {
        server.stop(0);
        assertThatThrownBy(() -> provider.verifyKey("sk_test_abcdefghijklmnop")).isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void threeDecimalCurrenciesMustBeSentInWholeHundredths() {
        PaymentProvider.CheckoutRequest odd = new PaymentProvider.CheckoutRequest(PAYMENT, new TenantId(UUID.randomUUID()), Money.of(1001, Currency.of("KWD")),
                "Wallet top-up", "https://a/b", "https://a/c");
        assertThatThrownBy(() -> provider.createCheckout(new PaymentProvider.Access("sk_test_k_1234567890", null), odd, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(seen).isEmpty();
    }
}
