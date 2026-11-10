package dev.ngb.core_billing.pricing.adapter.persistence;

import dev.ngb.core_billing.pricing.domain.plan.PlanId;
import dev.ngb.core_billing.pricing.domain.plan.PriceLine;
import dev.ngb.core_billing.pricing.domain.rating.RatingId;
import dev.ngb.core_billing.pricing.domain.rating.RatingStatus;
import dev.ngb.core_billing.pricing.domain.rating.UsageRating;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Table("usage_ratings")
record UsageRatingRow(
        @Id UUID id,
        UUID tenantId,
        UUID customerId,
        UUID walletId,
        UUID planId,
        int planVersion,
        String setKey,
        int attempt,
        Instant periodStart,
        Instant periodEnd,
        int usageCount,
        long amount,
        String currency,
        String status,
        UUID chargeId,
        String failureReason,
        String breakdown,
        Instant createdAt) {

    private record LineJson(String description, String metric, String quantity, String amount) {

        static LineJson of(PriceLine line) {
            return new LineJson(line.description(), line.metric(), line.quantity() == null ? null : line.quantity().toPlainString(),
                    line.amount().toPlainString());
        }

        PriceLine toLine() {
            return new PriceLine(description, metric, quantity == null ? null : new BigDecimal(quantity), new BigDecimal(amount));
        }
    }

    private static final TypeReference<List<LineJson>> LINES = new TypeReference<>() {};

    static UsageRatingRow from(UsageRating rating, JsonMapper json) {
        return new UsageRatingRow(
                rating.id().value(),
                rating.tenantId().value(),
                rating.customerId().value(),
                rating.walletId().value(),
                rating.planId().value(),
                rating.planVersion(),
                rating.setKey(),
                rating.attempt(),
                rating.periodStart(),
                rating.periodEnd(),
                rating.usageCount(),
                rating.amount().minorUnits(),
                rating.amount().currency().code(),
                rating.status().name(),
                rating.chargeId(),
                rating.failureReason(),
                json.writeValueAsString(rating.breakdown().stream().map(LineJson::of).toList()),
                rating.createdAt());
    }

    UsageRating toDomain(JsonMapper json) {
        return new UsageRating(
                new RatingId(id),
                new TenantId(tenantId),
                new CustomerId(customerId),
                new WalletId(walletId),
                new PlanId(planId),
                planVersion,
                setKey,
                attempt,
                periodStart,
                periodEnd,
                usageCount,
                Money.of(amount, Currency.of(currency.strip())),
                RatingStatus.valueOf(status),
                chargeId,
                failureReason,
                json.readValue(breakdown, LINES).stream().map(LineJson::toLine).toList(),
                createdAt);
    }
}
