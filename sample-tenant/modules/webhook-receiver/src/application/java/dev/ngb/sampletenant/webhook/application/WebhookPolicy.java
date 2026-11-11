package dev.ngb.sampletenant.webhook.application;

import dev.ngb.sampletenant.resource.api.ExecutionOrders;
import dev.ngb.sampletenant.webhook.application.port.CustomerPeriods;
import dev.ngb.sampletenant.webhook.application.port.DebtBook;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * The tenant decides what a Billing fact means for it. This reference policy records debt on a
 * failed payment, suspends resources when a subscription period cannot be paid, and resumes them
 * with an extended period when it is paid. Billing never imposes any of this.
 */
public class WebhookPolicy {

    public enum Decision {
        CONTINUE,
        RECORD_DEBT,
        CLEAR_DEBT,
        SUSPEND,
        EXTEND_PERIOD
    }

    private final DebtBook debts;
    private final CustomerPeriods periods;
    private final ExecutionOrders orders;
    private final Clock clock;

    public WebhookPolicy(DebtBook debts, CustomerPeriods periods, ExecutionOrders orders, Clock clock) {
        this.debts = debts;
        this.periods = periods;
        this.orders = orders;
        this.clock = clock;
    }

    public Decision apply(WebhookEnvelope event) {
        Instant now = clock.instant();
        return switch (event.type()) {
            case "payment.failed" -> recordDebt(event, now);
            case "invoice.paid" -> clearDebt(event, now);
            case "subscription.payment_failed" -> suspend(event, now);
            case "subscription.renewed" -> extend(event, now);
            default -> Decision.CONTINUE;
        };
    }

    private Decision recordDebt(WebhookEnvelope event, Instant now) {
        String customerId = string(event.data(), "customerId");
        Map<String, Object> amount = map(event.data(), "amount");
        Long minorUnits = number(amount, "minorUnits");
        if (customerId == null || minorUnits == null) {
            return Decision.CONTINUE;
        }
        debts.recordDebt(customerId, minorUnits, string(amount, "currency"), string(event.data(), "reason"), now);
        return Decision.RECORD_DEBT;
    }

    private Decision clearDebt(WebhookEnvelope event, Instant now) {
        String customerId = string(event.data(), "customerId");
        if (customerId == null) {
            return Decision.CONTINUE;
        }
        debts.clearOpenDebts(customerId, now);
        return Decision.CLEAR_DEBT;
    }

    private Decision suspend(WebhookEnvelope event, Instant now) {
        String customerId = string(event.data(), "customerId");
        if (customerId == null) {
            return Decision.CONTINUE;
        }
        orders.suspendByBillingCustomer(customerId, now);
        return Decision.SUSPEND;
    }

    private Decision extend(WebhookEnvelope event, Instant now) {
        String customerId = string(event.data(), "customerId");
        Instant periodEnd = instant(event.data(), "periodEnd");
        if (customerId == null || periodEnd == null) {
            return Decision.CONTINUE;
        }
        orders.resumeByBillingCustomer(customerId, now);
        periods.extend(customerId, periodEnd, now);
        return Decision.EXTEND_PERIOD;
    }

    private static String string(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value instanceof String text ? text : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value instanceof Map<?, ?> nested ? (Map<String, Object>) nested : Map.of();
    }

    private static Long number(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Instant instant(Map<String, Object> data, String key) {
        String value = string(data, key);
        try {
            return value == null ? null : Instant.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
