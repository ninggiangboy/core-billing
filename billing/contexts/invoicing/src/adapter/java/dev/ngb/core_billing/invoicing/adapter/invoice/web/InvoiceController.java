package dev.ngb.core_billing.invoicing.adapter.invoice.web;

import dev.ngb.core_billing.invoicing.application.invoice.command.InvoiceChargeUseCase;
import dev.ngb.core_billing.invoicing.application.invoice.command.ManageInvoiceUseCase;
import dev.ngb.core_billing.invoicing.application.invoice.command.PayInvoiceUseCase;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceItemView;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoicePaymentView;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceQueries;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceView;
import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceStatus;
import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.idempotency.RepeatableCreate;
import dev.ngb.core_billing.sharedkernel.money.MoneyDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Invoices. An invoice becomes paid only from a verified payment (or because the wallet already paid the charge it documents); no request here marks one paid. A Tenant System sees and acts on the customers mapped to it only. */
@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

    public record ItemResponse(
            String id, String source, String chargeId, String subscriptionId, Instant periodStart, Instant periodEnd, String description, MoneyDto amount,
            Instant incurredAt) {
        static ItemResponse of(InvoiceItemView i) {
            return new ItemResponse(i.id().toString(), i.source().name(), i.chargeId().toString(), i.subscriptionId() == null ? null : i.subscriptionId().toString(),
                    i.periodStart(), i.periodEnd(), i.description(), MoneyDto.of(i.amount()), i.incurredAt());
        }
    }

    public record InvoicePaymentResponse(String paymentId, MoneyDto amount, String status, String failureReason, String ledgerEntryId, Instant startedAt, Instant settledAt) {
        static InvoicePaymentResponse of(InvoicePaymentView p) {
            return new InvoicePaymentResponse(p.paymentId().toString(), MoneyDto.of(p.amount()), p.status().name(), p.failureReason(),
                    p.ledgerEntryId() == null ? null : p.ledgerEntryId().toString(), p.startedAt(), p.settledAt());
        }
    }

    public record InvoiceResponse(
            String id, String number, String customerId, String walletId, String status, String origin, MoneyDto total, Instant periodStart, Instant periodEnd,
            String settlement, String voidReason, Instant createdAt, Instant issuedAt, Instant paidAt, Instant voidedAt) {
        static InvoiceResponse of(InvoiceView i) {
            return new InvoiceResponse(i.id().toString(), i.number(), i.customerId().toString(), i.walletId().toString(), i.status().name(), i.origin().name(),
                    MoneyDto.of(i.total()), i.periodStart(), i.periodEnd(), i.settlement() == null ? null : i.settlement().name(), i.voidReason(), i.createdAt(),
                    i.issuedAt(), i.paidAt(), i.voidedAt());
        }
    }

    public record InvoiceDetailResponse(InvoiceResponse invoice, List<ItemResponse> items, List<InvoicePaymentResponse> payments) {
        static InvoiceDetailResponse of(InvoiceQueries.InvoiceDetail d) {
            return new InvoiceDetailResponse(InvoiceResponse.of(d.invoice()), d.items().stream().map(ItemResponse::of).toList(),
                    d.payments().stream().map(InvoicePaymentResponse::of).toList());
        }
    }

    /** {@code checkoutUrl} is Stripe's hosted page; it is present while the customer can still pay. */
    public record StartedPaymentResponse(String id, String status, String checkoutUrl) {}

    public record PayInvoiceResponse(InvoiceDetailResponse invoice, StartedPaymentResponse payment) {}

    public record InvoiceChargeRequest(String chargeId) {}

    public record VoidRequest(String reason) {}

    private final InvoiceQueries invoiceQueries;
    private final InvoiceChargeUseCase invoiceCharge;
    private final PayInvoiceUseCase payInvoice;
    private final ManageInvoiceUseCase manageInvoice;
    private final CurrentActor currentActor;

    public InvoiceController(
            InvoiceQueries invoiceQueries, InvoiceChargeUseCase invoiceCharge, PayInvoiceUseCase payInvoice, ManageInvoiceUseCase manageInvoice,
            CurrentActor currentActor) {
        this.invoiceQueries = invoiceQueries;
        this.invoiceCharge = invoiceCharge;
        this.payInvoice = payInvoice;
        this.manageInvoice = manageInvoice;
        this.currentActor = currentActor;
    }

    /** Newest first. Pass the last {@code id} of a page as {@code before} for the next one. */
    @GetMapping("/invoices")
    @RequiresPermission(Permission.INVOICE_READ)
    public List<InvoiceResponse> invoices(
            @RequestParam(required = false) String customerId, @RequestParam(required = false) String walletId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return invoiceQueries.list(customerId == null ? null : UUID.fromString(customerId), walletId == null ? null : UUID.fromString(walletId),
                status == null ? null : InvoiceStatus.valueOf(status), before == null ? null : UUID.fromString(before), limit, currentActor.caller())
                .stream().map(InvoiceResponse::of).toList();
    }

    @GetMapping("/invoices/{id}")
    @RequiresPermission(Permission.INVOICE_READ)
    public InvoiceDetailResponse invoice(@PathVariable String id) {
        return InvoiceDetailResponse.of(invoiceQueries.get(UUID.fromString(id), currentActor.caller()));
    }

    /**
     * Issues an invoice for one succeeded charge. 201 for a new invoice, 200 when the charge already had one, which is
     * returned: a charge is billed on one line at most, so no key is needed.
     */
    @PostMapping("/invoices")
    @RepeatableCreate(repeated = "The charge already had an invoice; that invoice is returned")
    @RequiresPermission(Permission.INVOICE_CREATE)
    public ResponseEntity<InvoiceDetailResponse> invoiceCharge(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody InvoiceChargeRequest request) {
        if (request.chargeId() == null) {
            throw new IllegalArgumentException("chargeId is required");
        }
        InvoiceChargeUseCase.Result result = invoiceCharge.execute(new InvoiceChargeUseCase.Command(ChargeId.of(request.chargeId()),
                Audit.of(currentActor.get(), requestId), currentActor.caller()));
        InvoiceQueries.InvoiceDetail detail = invoiceQueries.get(result.invoice().id().value(), currentActor.caller());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(InvoiceDetailResponse.of(detail));
    }

    /**
     * Starts a payment of an open invoice and returns Stripe's hosted page. The invoice is paid, and the debt paid off,
     * only when Stripe's verified result arrives. 201 for a new payment, 200 when the {@code Idempotency-Key} was used before.
     */
    @PostMapping("/invoices/{id}/payments")
    @RepeatableCreate
    @RequiresPermission(Permission.PAYMENT_CREATE)
    public ResponseEntity<PayInvoiceResponse> pay(
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        PayInvoiceUseCase.Result result = payInvoice.execute(new PayInvoiceUseCase.Command(InvoiceId.of(id), key, Audit.of(currentActor.get(), requestId),
                currentActor.caller()));
        InvoiceQueries.InvoiceDetail detail = invoiceQueries.get(result.invoice().id().value(), currentActor.caller());
        PayInvoiceResponse body = new PayInvoiceResponse(InvoiceDetailResponse.of(detail), new StartedPaymentResponse(result.payment().paymentId().toString(),
                result.payment().status(), result.payment().checkoutUrl()));
        return ResponseEntity.status(result.payment().replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    /** Cancels the request for payment. The wallet is not touched and a paid invoice cannot be voided (409). */
    @PostMapping("/invoices/{id}/void")
    @RequiresPermission(Permission.INVOICE_MANAGE)
    public InvoiceDetailResponse voidInvoice(
            @PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody VoidRequest request) {
        Invoice voided = manageInvoice.voidInvoice(InvoiceId.of(id), request.reason(), Audit.of(currentActor.get(), requestId));
        return InvoiceDetailResponse.of(invoiceQueries.get(voided.id().value(), currentActor.caller()));
    }

    /** Writes an open invoice off as unlikely to be paid. It can still be paid later. */
    @PostMapping("/invoices/{id}/uncollectible")
    @RequiresPermission(Permission.INVOICE_MANAGE)
    public InvoiceDetailResponse markUncollectible(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Invoice written = manageInvoice.markUncollectible(InvoiceId.of(id), Audit.of(currentActor.get(), requestId));
        return InvoiceDetailResponse.of(invoiceQueries.get(written.id().value(), currentActor.caller()));
    }
}
