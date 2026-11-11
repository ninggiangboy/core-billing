package dev.ngb.core_billing.payment.domain.refund;

import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** What refunds announce. Payloads are the integration contract: ids, amounts in minor units and codes, no personal data. */
public final class RefundEvents {

    private RefundEvents() {}

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
            return RefundEvents.payload("refundId", refund.id().toString(), "paymentId", refund.paymentId().toString(), "walletId",
                    payment.walletId().toString(), "amount", money(refund.amount()), "ledgerEntryId", refund.ledgerEntryId().toString());
        }
    }
}
