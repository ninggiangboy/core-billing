package dev.ngb.core_billing.boot.portal;

import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceItemView;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoicePaymentView;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceQueries;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceView;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionHistoryEntryView;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionQueries;
import dev.ngb.core_billing.invoicing.application.subscription.query.SubscriptionView;
import dev.ngb.core_billing.session.api.portal.PortalPermission;
import dev.ngb.core_billing.session.api.portal.RequiresPortalPermission;
import dev.ngb.core_billing.sharedkernel.money.MoneyDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The session's customer's invoices and subscriptions. */
@Profile("api")
@ConditionalOnProperty(name = "billing.portal.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/portal/api")
public class PortalInvoicingController {

    public record InvoiceResponse(
            String id, String number, String customerId, String walletId, String status, String origin, MoneyDto total, Instant periodStart,
            Instant periodEnd, String settlement, String voidReason, Instant createdAt, Instant issuedAt, Instant paidAt, Instant voidedAt) {
        static InvoiceResponse of(InvoiceView invoice) {
            return new InvoiceResponse(invoice.id().toString(), invoice.number(), invoice.customerId().toString(), invoice.walletId().toString(),
                    invoice.status().name(), invoice.origin().name(), MoneyDto.of(invoice.total()), invoice.periodStart(), invoice.periodEnd(),
                    invoice.settlement() == null ? null : invoice.settlement().name(), invoice.voidReason(), invoice.createdAt(), invoice.issuedAt(),
                    invoice.paidAt(), invoice.voidedAt());
        }
    }

    public record InvoiceItemResponse(
            String id, String source, String chargeId, String subscriptionId, Instant periodStart, Instant periodEnd, String description,
            MoneyDto amount, Instant incurredAt) {
        static InvoiceItemResponse of(InvoiceItemView item) {
            return new InvoiceItemResponse(item.id().toString(), item.source().name(), item.chargeId().toString(),
                    item.subscriptionId() == null ? null : item.subscriptionId().toString(), item.periodStart(), item.periodEnd(), item.description(),
                    MoneyDto.of(item.amount()), item.incurredAt());
        }
    }

    public record InvoicePaymentResponse(
            String paymentId, MoneyDto amount, String status, String failureReason, String ledgerEntryId, Instant startedAt, Instant settledAt) {
        static InvoicePaymentResponse of(InvoicePaymentView payment) {
            return new InvoicePaymentResponse(payment.paymentId().toString(), MoneyDto.of(payment.amount()), payment.status().name(),
                    payment.failureReason(), payment.ledgerEntryId() == null ? null : payment.ledgerEntryId().toString(), payment.startedAt(),
                    payment.settledAt());
        }
    }

    public record InvoiceDetailResponse(InvoiceResponse invoice, List<InvoiceItemResponse> items, List<InvoicePaymentResponse> payments) {
        static InvoiceDetailResponse of(InvoiceQueries.InvoiceDetail detail) {
            return new InvoiceDetailResponse(InvoiceResponse.of(detail.invoice()), detail.items().stream().map(InvoiceItemResponse::of).toList(),
                    detail.payments().stream().map(InvoicePaymentResponse::of).toList());
        }
    }

    public record SubscriptionResponse(
            String id, String customerId, String walletId, String planId, String planCode, String cycle, String status, Instant anchor,
            int periodsCharged, Instant currentPeriodStart, Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Instant canceledAt,
            Instant nextRenewalAt, int renewalAttempts, Instant createdAt) {
        static SubscriptionResponse of(SubscriptionView subscription) {
            return new SubscriptionResponse(subscription.id().toString(), subscription.customerId().toString(), subscription.walletId().toString(),
                    subscription.planId().toString(), subscription.planCode(), subscription.cycle().name(), subscription.status().name(),
                    subscription.anchor(), subscription.periodsCharged(), subscription.currentPeriodStart(), subscription.currentPeriodEnd(),
                    subscription.cancelAtPeriodEnd(), subscription.canceledAt(), subscription.nextRenewalAt(), subscription.renewalAttempts(),
                    subscription.createdAt());
        }
    }

    public record SubscriptionEventResponse(
            String id, String type, Integer periodNumber, Instant periodStart, Instant periodEnd, MoneyDto amount, String chargeId,
            String detail, Instant occurredAt) {
        static SubscriptionEventResponse of(SubscriptionHistoryEntryView entry) {
            return new SubscriptionEventResponse(entry.id().toString(), entry.type().name(), entry.periodNumber(), entry.periodStart(),
                    entry.periodEnd(), entry.amount() == null ? null : MoneyDto.of(entry.amount()),
                    entry.chargeId() == null ? null : entry.chargeId().toString(), entry.detail(), entry.occurredAt());
        }
    }

    public record SubscriptionDetailResponse(SubscriptionResponse subscription, List<SubscriptionEventResponse> history) {
        static SubscriptionDetailResponse of(SubscriptionQueries.SubscriptionDetail detail) {
            return new SubscriptionDetailResponse(SubscriptionResponse.of(detail.subscription()),
                    detail.history().stream().map(SubscriptionEventResponse::of).toList());
        }
    }

    private final PortalReadModel portal;

    public PortalInvoicingController(PortalReadModel portal) {
        this.portal = portal;
    }

    @GetMapping("/invoices")
    @RequiresPortalPermission(PortalPermission.INVOICE_VIEW)
    public List<InvoiceResponse> invoices(@RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return portal.invoices(before == null ? null : UUID.fromString(before), limit).stream().map(InvoiceResponse::of).toList();
    }

    @GetMapping("/invoices/{id}")
    @RequiresPortalPermission(PortalPermission.INVOICE_VIEW)
    public InvoiceDetailResponse invoice(@PathVariable String id) {
        return InvoiceDetailResponse.of(portal.invoice(UUID.fromString(id)));
    }

    @GetMapping("/subscriptions")
    @RequiresPortalPermission(PortalPermission.SUBSCRIPTION_VIEW)
    public List<SubscriptionResponse> subscriptions(@RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return portal.subscriptions(before == null ? null : UUID.fromString(before), limit).stream().map(SubscriptionResponse::of).toList();
    }

    @GetMapping("/subscriptions/{id}")
    @RequiresPortalPermission(PortalPermission.SUBSCRIPTION_VIEW)
    public SubscriptionDetailResponse subscription(@PathVariable String id) {
        return SubscriptionDetailResponse.of(portal.subscription(UUID.fromString(id)));
    }
}
