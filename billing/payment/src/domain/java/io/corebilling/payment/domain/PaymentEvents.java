package io.corebilling.payment.domain;

import io.corebilling.sharedkernel.DomainEvent;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the payment context announces, written to the outbox in the transaction of the change it reports.
 * Payloads are the integration contract: ids, amounts and codes, never a Stripe secret or card data.
 */
public final class PaymentEvents {

    private PaymentEvents() {}

    private static Map<String, Object> money(Money money) {
        return Map.of("minorUnits", money.minorUnits(), "currency", money.currency().code());
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }

    /** A top-up was paid and the wallet credited. */
    public record PaymentSucceeded(UUID eventId, Payment payment, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "payment.succeeded";

        @Override
        public TenantId tenantId() {
            return payment.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "payment/" + payment.id();
        }

        @Override
        public Map<String, Object> payload() {
            return PaymentEvents.payload("paymentId", payment.id().toString(), "walletId", payment.walletId().toString(), "customerId",
                    payment.customerId().toString(), "amount", money(payment.amount()), "ledgerEntryId", payment.ledgerEntryId().toString());
        }
    }

    /** A top-up ended without money: Stripe could not collect it. */
    public record PaymentFailed(UUID eventId, Payment payment, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "payment.failed";

        @Override
        public TenantId tenantId() {
            return payment.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "payment/" + payment.id();
        }

        @Override
        public Map<String, Object> payload() {
            return PaymentEvents.payload("paymentId", payment.id().toString(), "walletId", payment.walletId().toString(), "customerId",
                    payment.customerId().toString(), "amount", money(payment.amount()), "reason", payment.failureReason());
        }
    }

    /** Money went back to the customer's card and the wallet was debited for it. */
    public record RefundSucceeded(UUID eventId, Refund refund, Payment payment, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "refund.succeeded";

        @Override
        public TenantId tenantId() {
            return refund.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "payment/" + refund.paymentId();
        }

        @Override
        public Map<String, Object> payload() {
            return PaymentEvents.payload("refundId", refund.id().toString(), "paymentId", refund.paymentId().toString(), "walletId",
                    payment.walletId().toString(), "amount", money(refund.amount()), "ledgerEntryId", refund.ledgerEntryId().toString());
        }
    }
}
