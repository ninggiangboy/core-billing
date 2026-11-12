package dev.ngb.tenant.billing.adapter.client.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.ngb.tenant.billing.adapter.client.http.HttpBillingSettings;
import dev.ngb.tenant.billing.api.client.BillingCharges.CaptureRequest;
import dev.ngb.tenant.billing.api.client.BillingCharges.ChargeRequest;
import dev.ngb.tenant.billing.api.client.BillingCustomers.NewCustomer;
import dev.ngb.tenant.billing.api.client.BillingPricing.EstimateRequest;
import dev.ngb.tenant.billing.api.client.BillingPricing.ReserveRequest;
import dev.ngb.tenant.billing.api.model.Charge;
import dev.ngb.tenant.billing.api.model.Customer;
import dev.ngb.tenant.billing.api.model.Estimate;
import dev.ngb.tenant.billing.api.model.RecordedUsage;
import dev.ngb.tenant.billing.api.model.ReserveResult;
import dev.ngb.tenant.billing.api.model.UsageEventRequest;
import dev.ngb.tenant.billing.api.model.Wallet;
import dev.ngb.tenant.shared.error.NotFoundException;
import dev.ngb.tenant.shared.error.PaymentRequiredException;
import dev.ngb.tenant.shared.error.UpstreamUnavailableException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeignBillingClientTest {

    private FakeBilling billing;
    private FeignBillingClient client;

    @BeforeEach
    void start() throws IOException {
        billing = new FakeBilling();
        client = clientWith("cbk_key_secret");
    }

    @AfterEach
    void stop() {
        billing.close();
    }

    @Test
    void createCustomerSendsTheCredentialAndIdempotencyKey() {
        billing.respond(201, """
                {"id":"cus_1","displayName":"Ada","email":"ada@example.com","status":"ACTIVE","createdAt":"2026-11-11T00:00:00Z"}
                """);

        Customer customer = client.create("key-1", new NewCustomer("sys_1", "user_1", "Ada", "ada@example.com"));

        assertThat(customer.id()).isEqualTo("cus_1");
        FakeBilling.Request request = billing.request(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/customers");
        assertThat(request.header("Authorization")).isEqualTo("Bearer cbk_key_secret");
        assertThat(request.header("Idempotency-Key")).isEqualTo("key-1");
        assertThat(request.header("Content-Type")).isEqualTo("application/json");
        assertThat(request.header("Accept")).isEqualTo("application/json");
        assertThat(request.body()).contains("\"externalUserId\":\"user_1\"");
    }

    @Test
    void estimateMapsTheNestedAmountAndLines() {
        billing.respond(200, """
                {"planId":"plan_1","planCode":"compute","planVersion":2,
                 "amount":{"amount":1250,"currency":"USD"},"exactAmount":"12.5","adjustment":"0",
                 "lines":[{"description":"compute","metric":"vCPU","quantity":"2","amount":"12.5"}]}
                """);

        Estimate estimate = client.estimate(new EstimateRequest("plan_1", null, null, List.of()));

        assertThat(estimate.planVersion()).isEqualTo(2);
        assertThat(estimate.amount().money().minorUnits()).isEqualTo(1250);
        assertThat(estimate.lines()).singleElement().satisfies(line -> assertThat(line.metric()).isEqualTo("vCPU"));
        assertThat(billing.request(0).path()).isEqualTo("/api/v1/pricing/estimates");
    }

    @Test
    void reserveFromEstimateCarriesTheSameIdempotencyKey() {
        billing.respond(201, """
                {"estimate":{"planId":"plan_1","planCode":"compute","planVersion":1,
                  "amount":{"amount":1000,"currency":"USD"},"lines":[]},
                 "reservation":{"id":"res_1","status":"HELD","amount":{"amount":1000,"currency":"USD"},
                  "expiresAt":"2026-11-11T01:00:00Z","replayed":false}}
                """);

        ReserveResult result = client.reserve(
                "key-2", new ReserveRequest("wal_1", 1000, "USD", 3600L, "order-1"));

        assertThat(result.reservation().id()).isEqualTo("res_1");
        assertThat(result.estimate().amount().money().currency()).isEqualTo("USD");
        assertThat(billing.request(0).header("Idempotency-Key")).isEqualTo("key-2");
    }

    @Test
    void captureAndReleaseUseTheReservationPath() {
        billing.respond(201, """
                {"id":"chg_1","status":"SUCCEEDED","amount":{"amount":900,"currency":"USD"},"reservationId":"res_1"}
                """);
        billing.respond(200, """
                {"id":"res_1","walletId":"wal_1","status":"CAPTURED","amount":{"amount":1000,"currency":"USD"},"captured":{"amount":900,"currency":"USD"}}
                """);

        Charge charge = client.capture("key-3", "res_1", new CaptureRequest(900, "USD", "order-1"));
        var reservation = client.release("res_1");

        assertThat(charge.id()).isEqualTo("chg_1");
        assertThat(reservation.status()).isEqualTo("CAPTURED");
        assertThat(billing.request(0).path()).isEqualTo("/api/v1/reservations/res_1/capture");
        assertThat(billing.request(1).path()).isEqualTo("/api/v1/reservations/res_1/release");
    }

    @Test
    void recordsUsageWithTheKeyInTheBody() {
        billing.respond(201, """
                {"event":{"id":"use_1","customerId":"cus_1","metric":"vCPU","quantity":"2","recordedAt":"2026-11-11T00:00:00Z"},
                 "replayed":false}
                """);

        RecordedUsage recorded = client.record(new UsageEventRequest(
                "cus_1", "vCPU", "2", "minute", Instant.parse("2026-11-11T00:00:00Z"), "usage-key", "order-1", null));

        assertThat(recorded.event().id()).isEqualTo("use_1");
        assertThat(billing.request(0).body()).contains("\"idempotencyKey\":\"usage-key\"");
    }

    @Test
    void walletReadsCarryTheCustomerQuery() {
        billing.respond(200, """
                [{"id":"wal_1","customerId":"cus_1","currency":"USD","mode":"PREPAID",
                  "balance":{"amount":500,"currency":"USD"},"held":{"amount":0,"currency":"USD"},
                  "available":{"amount":500,"currency":"USD"}}]
                """);

        List<Wallet> wallets = client.wallets("cus_1");

        assertThat(wallets).singleElement().satisfies(wallet -> assertThat(wallet.mode()).isEqualTo("PREPAID"));
        assertThat(billing.request(0).path()).isEqualTo("/api/v1/wallets?customerId=cus_1");
    }

    @Test
    void mapsBillingErrorsToDomainErrors() {
        billing.respond(404, "{\"detail\":\"customer not found\"}");
        billing.respond(402, "{\"detail\":\"The wallet cannot cover this amount.\"}");

        assertThatThrownBy(() -> client.get("cus_missing"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("customer not found");
        assertThatThrownBy(() -> client.charge("key", new ChargeRequest("wal_1", 9999, "USD", null, "order-1")))
                .isInstanceOf(PaymentRequiredException.class)
                .hasMessage("The wallet cannot cover this amount.");
    }

    @Test
    void retriesAServerErrorWithTheSameIdempotencyKey() {
        billing.respond(500, "{\"detail\":\"temporary\"}");
        billing.respond(201, """
                {"id":"cus_1","displayName":"Ada","email":"ada@example.com","status":"ACTIVE"}
                """);

        Customer customer = client.create("key-retry", new NewCustomer("sys_1", "user_1", "Ada", "ada@example.com"));

        assertThat(customer.id()).isEqualTo("cus_1");
        assertThat(billing.requests()).hasSize(2);
        assertThat(billing.request(0).header("Idempotency-Key")).isEqualTo("key-retry");
        assertThat(billing.request(1).header("Idempotency-Key")).isEqualTo("key-retry");
    }

    @Test
    void givesUpAfterTheConfiguredAttempts() {
        billing.respond(500, "{\"detail\":\"temporary\"}");
        billing.respond(500, "{\"detail\":\"temporary\"}");

        assertThatThrownBy(() -> client.get("cus_1")).isInstanceOf(UpstreamUnavailableException.class);

        assertThat(billing.requests()).hasSize(2);
    }

    @Test
    void omitsAnAbsentQueryParameter() {
        billing.respond(200, "[]");

        client.plans(null);

        assertThat(billing.request(0).path()).isEqualTo("/api/v1/pricing-plans");
    }

    @Test
    void refusesAWriteWithoutAnIdempotencyKeyBeforeAnyRequest() {
        NewCustomer customer = new NewCustomer("sys_1", "user_1", "Ada", "ada@example.com");

        assertThatThrownBy(() -> client.create(null, customer)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.charge(null, new ChargeRequest("wal_1", 1, "USD", null, "order-1")))
                .isInstanceOf(NullPointerException.class);

        assertThat(billing.requests()).isEmpty();
    }

    @Test
    void encodesReservedCharactersInAPathValue() {
        billing.respond(200, "{\"id\":\"cus_1\"}");

        client.byExternalUser("a/b c", "sys_1");

        assertThat(billing.request(0).path())
                .isEqualTo("/api/v1/customers/by-external-user/a%2Fb%20c?tenantSystemId=sys_1");
    }

    @Test
    void ignoresUnknownJsonFields() {
        billing.respond(200, """
                {"id":"cus_1","displayName":"Ada","status":"ACTIVE","unexpected":{"nested":true}}
                """);

        Customer customer = client.get("cus_1");

        assertThat(customer.id()).isEqualTo("cus_1");
    }

    @Test
    void sendsNoCredentialWhenTheKeyIsBlank() {
        billing.respond(200, "[]");
        clientWith("").plans(null);

        assertThat(billing.request(0).header("Authorization")).isNull();
    }

    private FeignBillingClient clientWith(String apiKey) {
        return BillingFeignClientFactory.create(new HttpBillingSettings(
                billing.baseUrl(), apiKey, Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ofMillis(1)));
    }

    private static final class FakeBilling implements AutoCloseable {

        record Request(String method, String path, Map<String, List<String>> headers, String body) {
            String header(String name) {
                return headers.entrySet().stream()
                        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                        .map(entry -> entry.getValue().get(0))
                        .findFirst()
                        .orElse(null);
            }
        }

        private record Response(int status, String body) {}

        private final HttpServer server;
        private final Deque<Response> responses = new ArrayDeque<>();
        private final List<Request> requests = new ArrayList<>();

        FakeBilling() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        void respond(int status, String body) {
            responses.add(new Response(status, body));
        }

        List<Request> requests() {
            return requests;
        }

        Request request(int index) {
            return requests.get(index);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath()
                            + (exchange.getRequestURI().getRawQuery() == null
                                    ? ""
                                    : "?" + exchange.getRequestURI().getRawQuery()),
                    exchange.getRequestHeaders(),
                    new String(body, StandardCharsets.UTF_8)));
            Response response = responses.isEmpty() ? new Response(500, "{}") : responses.remove();
            byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
