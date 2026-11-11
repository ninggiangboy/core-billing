package dev.ngb.core_billing.payment.adapter.refund.web;

import dev.ngb.core_billing.payment.application.refund.command.RequestRefundUseCase;
import dev.ngb.core_billing.payment.application.refund.query.RefundQueries;
import dev.ngb.core_billing.payment.application.refund.query.RefundView;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.refund.Refund;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.money.MoneyDto;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RestController;

/** Refunds of payments. Money-moving POSTs need an {@code Idempotency-Key}. */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1")
public class RefundController {

    public record RefundRequest(Long amount, String currency, String reason) {}

    public record RefundResponse(
            String id, String paymentId, String status, MoneyDto amount, String reason, String failureReason, String ledgerEntryId, Instant createdAt,
            Instant completedAt) {
        static RefundResponse of(Refund r) {
            return new RefundResponse(r.id().toString(), r.paymentId().toString(), r.status().name(), MoneyDto.of(r.amount()), r.reason(),
                    r.failureReason(), r.ledgerEntryId() == null ? null : r.ledgerEntryId().toString(), r.createdAt(), r.completedAt());
        }

        public static RefundResponse ofView(RefundView r) {
            return new RefundResponse(r.id().toString(), r.paymentId().toString(), r.status().name(), MoneyDto.of(r.amount()), r.reason(),
                    r.failureReason(), r.ledgerEntryId() == null ? null : r.ledgerEntryId().toString(), r.createdAt(), r.completedAt());
        }
    }

    private final RequestRefundUseCase refund;
    private final RefundQueries refundQueries;
    private final CurrentActor currentActor;

    public RefundController(RequestRefundUseCase refund, RefundQueries refundQueries, CurrentActor currentActor) {
        this.refund = refund;
        this.refundQueries = refundQueries;
        this.currentActor = currentActor;
    }

    /**
     * Refunds a payment, in full when {@code amount} is omitted. The unspent balance is held at once, Stripe is
     * asked, and the wallet is debited when Stripe confirms. 409 when the amount exceeds what is left to refund
     * or the wallet has already spent it.
     */
    @PostMapping("/payments/{id}/refunds")
    @RequiresPermission(Permission.REFUND_CREATE)
    public ResponseEntity<RefundResponse> requestRefund(
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @RequestBody RefundRequest request) {
        Money amount = null;
        if (request.amount() != null) {
            if (request.currency() == null) {
                throw new IllegalArgumentException("currency is required with an amount");
            }
            amount = Money.of(request.amount(), Currency.of(request.currency()));
        }
        RequestRefundUseCase.Result result = refund.execute(new RequestRefundUseCase.Command(PaymentId.of(id), amount, request.reason(), key, Audit.of(currentActor.get(), requestId)));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(RefundResponse.of(result.refund()));
    }

    @GetMapping("/refunds/{id}")
    @RequiresPermission(Permission.PAYMENT_READ)
    public RefundResponse getRefund(@PathVariable String id) {
        return RefundResponse.ofView(refundQueries.get(UUID.fromString(id), currentActor.caller()));
    }
}
