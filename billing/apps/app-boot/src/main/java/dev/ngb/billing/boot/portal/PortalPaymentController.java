package dev.ngb.billing.boot.portal;

import dev.ngb.billing.payment.application.payment.query.PaymentAttemptView;
import dev.ngb.billing.payment.application.payment.query.PaymentQueries;
import dev.ngb.billing.payment.application.payment.query.PaymentView;
import dev.ngb.billing.payment.application.refund.query.RefundView;
import dev.ngb.billing.payment.domain.payment.PaymentStatus;
import dev.ngb.billing.session.api.portal.PortalPermission;
import dev.ngb.billing.session.api.portal.RequiresPortalPermission;
import dev.ngb.billing.sharedkernel.money.MoneyDto;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The session's customer's payments, refunds and the Stripe-hosted page for managing payment methods. */
@Profile("api")
@ConditionalOnProperty(name = "billing.portal.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/portal/api")
public class PortalPaymentController {

    public record PaymentResponse(
            String id, String walletId, String status, MoneyDto amount, MoneyDto refunded, String checkoutUrl, String failureReason,
            Instant createdAt, Instant updatedAt, Instant completedAt) {
        static PaymentResponse of(PaymentView payment) {
            String url = payment.status() == PaymentStatus.REQUIRES_ACTION ? payment.checkoutUrl() : null;
            return new PaymentResponse(payment.id().toString(), payment.walletId().toString(), payment.status().name(), MoneyDto.of(payment.amount()),
                    MoneyDto.of(payment.refunded()), url, payment.failureReason(), payment.createdAt(), payment.updatedAt(), payment.completedAt());
        }
    }

    public record AttemptResponse(String failureCode, String failureMessage, Instant occurredAt) {
        static AttemptResponse of(PaymentAttemptView attempt) {
            return new AttemptResponse(attempt.failureCode(), attempt.failureMessage(), attempt.occurredAt());
        }
    }

    public record PaymentDetailResponse(PaymentResponse payment, List<RefundResponse> refunds, List<AttemptResponse> failedAttempts) {}

    public record RefundResponse(
            String id, String paymentId, String status, MoneyDto amount, String reason, String failureReason, Instant createdAt, Instant completedAt) {
        static RefundResponse of(RefundView refund) {
            return new RefundResponse(refund.id().toString(), refund.paymentId().toString(), refund.status().name(), MoneyDto.of(refund.amount()),
                    refund.reason(), refund.failureReason(), refund.createdAt(), refund.completedAt());
        }
    }

    public record HostedPageResponse(String url) {}

    private final PortalReadModel portal;

    public PortalPaymentController(PortalReadModel portal) {
        this.portal = portal;
    }

    @GetMapping("/payments")
    @RequiresPortalPermission(PortalPermission.PAYMENT_VIEW)
    public List<PaymentResponse> payments(@RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return portal.payments(before == null ? null : UUID.fromString(before), limit).stream().map(PaymentResponse::of).toList();
    }

    @GetMapping("/payments/{id}")
    @RequiresPortalPermission(PortalPermission.PAYMENT_VIEW)
    public PaymentDetailResponse payment(@PathVariable String id) {
        PaymentQueries.PaymentDetail detail = portal.payment(UUID.fromString(id));
        return new PaymentDetailResponse(PaymentResponse.of(detail.payment()), detail.refunds().stream().map(RefundResponse::of).toList(),
                detail.attempts().stream().map(AttemptResponse::of).toList());
    }

    @GetMapping("/refunds")
    @RequiresPortalPermission(PortalPermission.REFUND_VIEW)
    public List<RefundResponse> refunds(@RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return portal.refunds(before == null ? null : UUID.fromString(before), limit).stream().map(RefundResponse::of).toList();
    }

    /**
     * Opens Stripe's hosted page for managing payment methods. The provider customer is created once per Billing
     * Customer and Stripe configuration, and the page is the Tenant's own Stripe account.
     */
    @PostMapping("/payment-methods")
    @RequiresPortalPermission(PortalPermission.PAYMENT_METHOD_MANAGE)
    public HostedPageResponse paymentMethods(HttpServletRequest request) {
        return new HostedPageResponse(portal.paymentMethodsLink(PortalUrls.origin(request) + "/portal/payment-methods/done"));
    }
}
