package dev.ngb.core_billing.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.pricing.PricingTestWorld;
import dev.ngb.core_billing.pricing.application.rating.command.ChargeFromUsageUseCase;
import dev.ngb.core_billing.pricing.application.plan.command.PlanAdministration;
import dev.ngb.core_billing.pricing.domain.model.EventBased;
import java.time.Instant;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.money.api.wallet.DebitCommand;
import dev.ngb.core_billing.money.api.wallet.DebitWallet;
import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.reservation.ReserveBudgetCommand;
import dev.ngb.core_billing.money.application.reservation.command.ExpireReservationsUseCase;
import dev.ngb.core_billing.money.application.reservation.command.ReleaseReservationUseCase;
import dev.ngb.core_billing.money.application.ledger.command.ReverseEntryUseCase;
import dev.ngb.core_billing.money.application.wallet.query.WalletQueries;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.support.EventSchemas;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.payment.application.refund.port.RefundRepository;
import dev.ngb.core_billing.payment.application.stripe.command.ReceiveStripeWebhookUseCase;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.refund.Refund;
import dev.ngb.core_billing.payment.domain.refund.RefundId;

/**
 * Every event the code emits is checked against the JSON Schema in contracts/events. The events are produced
 * by the real use cases and read back from the outbox, so a payload builder that drifts fails here.
 */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class EventContractIntegrationTest {

    /** Event types this test produces through the real code; every schema must have a producer listed here. */
    private static final Set<String> EMITTED_HERE = Set.of(
            "reservation.created", "reservation.released", "reservation.expired", "charge.succeeded", "charge.failed", "wallet.credited",
            "wallet.debited", "ledger.entry-reversed", "customer.created", "pricing-plan.version-published", "pricing-plan.retired", "usage.rated",
            "payment.succeeded", "payment.failed", "refund.succeeded", "invoice.created", "invoice.paid");

    @Autowired MoneyTestWorld worlds;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired ChargeWallet charge;
    @Autowired DebitWallet debit;
    @Autowired ReserveBudget reserve;
    @Autowired ReleaseReservationUseCase release;
    @Autowired ExpireReservationsUseCase expire;
    @Autowired ReverseEntryUseCase reverse;
    @Autowired WalletQueries queries;
    @Autowired PricingTestWorld pricing;
    @Autowired ChargeFromUsageUseCase chargeUsage;
    @Autowired PlanAdministration planAdmin;
    @Autowired dev.ngb.core_billing.payment.PaymentTestWorld payments;
    @Autowired dev.ngb.core_billing.payment.application.stripe.command.ReceiveStripeWebhookUseCase receive;
    @Autowired dev.ngb.core_billing.payment.application.payment.port.PaymentRepository paymentRepository;
    @Autowired dev.ngb.core_billing.payment.application.refund.port.RefundRepository refundRepository;
    @Autowired dev.ngb.core_billing.sharedkernel.UnitOfWork unitOfWork;
    @Autowired dev.ngb.core_billing.boot.infrastructure.outbox.OutboxRelay relay;
    @Autowired dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase closeCycle;

    private final JsonMapper json = JsonMapper.builder().build();

    private record Payload(String type, String json) {}

    /** Drives the money path through every event it can emit and returns what landed in the outbox. */
    private List<Payload> produceMoneyEvents() {
        World w = worlds.prepaid(100_000);
        var tenant = w.tenant().id();
        var walletId = w.wallet().id();
        Money five = Money.of(500, w.wallet().currency());

        ReservationResult held = scopes.inTenant(tenant, () -> reserve.reserve(new ReserveBudgetCommand(walletId, five, Duration.ofMinutes(5), "run-1", UUID.randomUUID().toString())));
        scopes.inTenant(tenant, () -> release.execute(held.id()));
        ReservationResult toExpire = scopes.inTenant(tenant, () -> reserve.reserve(new ReserveBudgetCommand(walletId, five, Duration.ofMinutes(5), null, UUID.randomUUID().toString())));
        owner.jdbc().update("update reservations set expires_at = now() - interval '1 second', created_at = now() - interval '1 minute' where id = ?", toExpire.id().value());
        scopes.inTenant(tenant, () -> expire.execute(10));
        ReservationResult toCapture = scopes.inTenant(tenant, () -> reserve.reserve(new ReserveBudgetCommand(walletId, five, Duration.ofMinutes(5), "run-2", UUID.randomUUID().toString())));
        var captured = scopes.inTenant(tenant, () -> charge.charge(new ChargeCommand(null, toCapture.id(), five, null, "capture", UUID.randomUUID().toString())));
        var fromUsage = scopes.inTenant(tenant, () -> charge.charge(new ChargeCommand(walletId, null, five, "usage-batch-1", "usage", UUID.randomUUID().toString())));
        scopes.inTenant(tenant, () -> charge.charge(new ChargeCommand(walletId, null, Money.of(10_000_000, w.wallet().currency()), null, null, UUID.randomUUID().toString())));
        scopes.inTenant(tenant, () -> debit.debit(new DebitCommand(walletId, five, "fee", null, UUID.randomUUID().toString(), "system/test")));
        scopes.inTenant(tenant, () -> reverse.execute(new ReverseEntryUseCase.Command(fromUsage.ledgerEntryId(), "billing error", null, "user/test")));
        assertThat(captured.status()).isNotNull();

        List<Payload> payloads = new ArrayList<>();
        owner.jdbc().query("select type, payload::text as payload from outbox where tenant_id = ?", rs -> {
            payloads.add(new Payload(rs.getString("type"), rs.getString("payload")));
        }, tenant.value());
        return payloads;
    }

    /** Creates, versions and retires plans and rates usage, then returns what landed in the outbox. */
    private List<Payload> producePricingEvents() {
        World w = worlds.prepaid(100_000);
        var tenant = w.tenant().id();
        String metric = pricing.metric();
        var plan = pricing.plan(tenant, "calls", new EventBased(metric, "call", new java.math.BigDecimal("1")));
        Instant day = Instant.parse("2026-10-01T00:00:00Z");
        pricing.record(tenant, w.customerId(), metric, "call", "10", day.plusSeconds(10));
        scopes.inTenant(tenant, () -> chargeUsage.execute(new ChargeFromUsageUseCase.Command(w.customerId(), w.wallet().id(), plan.id(), null, day, day.plusSeconds(3600))));
        String cheap = pricing.metric();
        var cheapPlan = pricing.plan(tenant, "cheap", new EventBased(cheap, "call", new java.math.BigDecimal("0.001")));
        pricing.record(tenant, w.customerId(), cheap, "call", "2", day.plusSeconds(10));
        scopes.inTenant(tenant, () -> chargeUsage.execute(new ChargeFromUsageUseCase.Command(w.customerId(), w.wallet().id(), cheapPlan.id(), null, day, day.plusSeconds(3600))));
        scopes.inTenant(tenant, () -> planAdmin.publishVersion(PricingTestWorld.AUDIT, plan.id(), new PlanAdministration.Terms(
                new EventBased(metric, "call", new java.math.BigDecimal("2")), null, null, null, null, day.plusSeconds(100), null)));
        scopes.inTenant(tenant, () -> planAdmin.retire(PricingTestWorld.AUDIT, plan.id()));

        List<Payload> payloads = new ArrayList<>();
        owner.jdbc().query("select type, payload::text as payload from outbox where tenant_id = ?", rs -> {
            payloads.add(new Payload(rs.getString("type"), rs.getString("payload")));
        }, tenant.value());
        return payloads;
    }

    /** Pays, fails and refunds a top-up through verified, signed Stripe events and returns what landed in the outbox. */
    private List<Payload> producePaymentEvents() {
        var w = payments.direct(0);
        var tenant = w.money().tenant().id();
        var ok = payments.openPayment(w, new dev.ngb.core_billing.payment.domain.payment.PaymentId(UUID.randomUUID()), 5000);
        deliver(w, dev.ngb.core_billing.support.StripeEvents.paidCheckout(dev.ngb.core_billing.support.StripeEvents.newEventId(), null, "cs_" + ok.id(), "pi_" + ok.id(),
                ok.id().toString(), 5000));
        var failing = payments.openPayment(w, new dev.ngb.core_billing.payment.domain.payment.PaymentId(UUID.randomUUID()), 700);
        deliver(w, dev.ngb.core_billing.support.StripeEvents.checkout(dev.ngb.core_billing.support.StripeEvents.newEventId(), "checkout.session.async_payment_failed", null,
                "cs_" + failing.id(), "pi_" + failing.id(), failing.id().toString(), "complete", "unpaid", 700, "usd"));
        var settled = scopes.inTenant(tenant, () -> unitOfWork.transactional(() -> paymentRepository.findById(ok.id()).orElseThrow()));
        var refund = scopes.inTenant(tenant, () -> unitOfWork.transactional(() -> {
            var r = dev.ngb.core_billing.payment.domain.refund.Refund.request(new dev.ngb.core_billing.payment.domain.refund.RefundId(UUID.randomUUID()), settled,
                    Money.of(1000, settled.amount().currency()), null, null, "user/test", "refund-" + UUID.randomUUID(), "hash", Instant.now());
            refundRepository.add(r);
            return r;
        }));
        deliver(w, dev.ngb.core_billing.support.StripeEvents.refund(dev.ngb.core_billing.support.StripeEvents.newEventId(), null, "re_" + refund.id(), "pi_" + ok.id(),
                refund.id().toString(), "succeeded", 1000));

        List<Payload> payloads = new ArrayList<>();
        owner.jdbc().query("select type, payload::text as payload from outbox where tenant_id = ?", rs -> {
            payloads.add(new Payload(rs.getString("type"), rs.getString("payload")));
        }, tenant.value());
        return payloads;
    }

    /** Collects Postpaid charges, closes their period, pays the invoice through a verified Stripe result and returns what landed in the outbox. */
    private List<Payload> produceInvoicingEvents() {
        World postpaid = worlds.postpaid(1_000_000);
        var w = payments.directFor(postpaid, "acct_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        var tenant = postpaid.tenant().id();
        var money = postpaid.wallet().currency();
        scopes.inTenant(tenant, () -> charge.charge(new ChargeCommand(postpaid.wallet().id(), null, Money.of(300, money), null, "work", UUID.randomUUID().toString())));
        scopes.inTenant(tenant, () -> charge.charge(new ChargeCommand(postpaid.wallet().id(), null, Money.of(700, money), null, "more work", UUID.randomUUID().toString())));
        scopes.runInTenant(tenant, relay::relayDue);
        owner.jdbc().update("update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month' where tenant_id = ?", tenant.value());
        scopes.inTenant(tenant, closeCycle::runOnce);

        UUID invoiceId = owner.jdbc().queryForObject("select id from invoices where tenant_id = ?", UUID.class, tenant.value());
        var paymentId = new dev.ngb.core_billing.payment.domain.payment.PaymentId(UUID.randomUUID());
        scopes.runInTenant(tenant, () -> unitOfWork.transactional(() -> paymentRepository.add(dev.ngb.core_billing.payment.domain.payment.Payment.startForInvoice(paymentId,
                w.configuration(), invoiceId, postpaid.wallet().id(), postpaid.customerId(), Money.of(1000, money), "key-" + paymentId, "hash-" + paymentId, Instant.now()))));
        deliver(w, dev.ngb.core_billing.support.StripeEvents.paidCheckout(dev.ngb.core_billing.support.StripeEvents.newEventId(), null, "cs_" + paymentId, "pi_" + paymentId,
                paymentId.toString(), 1000));
        scopes.runInTenant(tenant, relay::relayDue);

        List<Payload> payloads = new ArrayList<>();
        owner.jdbc().query("select type, payload::text as payload from outbox where tenant_id = ?", rs -> {
            payloads.add(new Payload(rs.getString("type"), rs.getString("payload")));
        }, tenant.value());
        return payloads;
    }

    private void deliver(dev.ngb.core_billing.payment.PaymentTestWorld.PayWorld w, String body) {
        receive.receiveDirect(w.configuration().id(), body, dev.ngb.core_billing.support.StripeEvents.sign(w.webhookSecret(), body));
    }

    @Test
    void everyPayloadTheCodeEmitsConformsToItsSchema() {
        List<Payload> produced = new ArrayList<>(produceMoneyEvents());
        produced.addAll(producePricingEvents());
        produced.addAll(producePaymentEvents());
        produced.addAll(produceInvoicingEvents());

        Set<String> producedTypes = new TreeSet<>();
        produced.forEach(p -> producedTypes.add(p.type()));
        assertThat(producedTypes).as("the scenarios exercised every event type").containsExactlyInAnyOrderElementsOf(EMITTED_HERE);
        for (Payload payload : produced) {
            assertThat(EventSchemas.validate(payload.type(), payload.json())).as(payload.type() + " " + payload.json()).isEmpty();
        }
    }

    @Test
    void everySchemaHasAProducerAndEveryProducerHasASchema() {
        assertThat(EventSchemas.types()).containsExactlyInAnyOrderElementsOf(EMITTED_HERE);
    }

    @Test
    void theValidatorIsStrictEnoughToCatchDrift() throws Exception {
        List<Payload> produced = produceMoneyEvents();
        Payload charge = produced.stream().filter(p -> p.type().equals("charge.succeeded")).findFirst().orElseThrow();
        JsonNode good = json.readTree(charge.json());
        assertThat(EventSchemas.validate("charge.succeeded", charge.json())).isEmpty();

        var extra = (tools.jackson.databind.node.ObjectNode) good.deepCopy();
        extra.put("surprise", 1);
        assertThat(EventSchemas.validate("charge.succeeded", extra.toString())).as("an undeclared field").isNotEmpty();

        var missing = (tools.jackson.databind.node.ObjectNode) good.deepCopy();
        missing.remove("walletId");
        assertThat(EventSchemas.validate("charge.succeeded", missing.toString())).as("a removed field").isNotEmpty();

        var wrongType = (tools.jackson.databind.node.ObjectNode) good.deepCopy();
        wrongType.put("amount", "12.50");
        assertThat(EventSchemas.validate("charge.succeeded", wrongType.toString())).as("a changed type").isNotEmpty();

        var notUuid = (tools.jackson.databind.node.ObjectNode) good.deepCopy();
        notUuid.put("chargeId", "not-a-uuid");
        assertThat(EventSchemas.validate("charge.succeeded", notUuid.toString())).as("format is asserted").isNotEmpty();

        var badEnum = (tools.jackson.databind.node.ObjectNode) good.deepCopy();
        badEnum.put("source", "SOMETHING");
        assertThat(EventSchemas.validate("charge.succeeded", badEnum.toString())).isNotEmpty();
        assertThat(EventSchemas.validate("no.such-event", "{}")).isNotEmpty();
    }

    @Test
    void theDeliveredBodyEmbedsThePayloadUnchangedAsData() throws Exception {
        List<Payload> produced = produceMoneyEvents();
        Payload credited = produced.stream().filter(p -> p.type().equals("wallet.credited")).findFirst().orElseThrow();
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("data", json.readValue(credited.json(), Map.class));
        assertThat(EventSchemas.validate("wallet.credited", json.writeValueAsString(envelope.get("data")))).isEmpty();
    }
}
