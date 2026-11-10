package io.corebilling.pricing.adapter.persistence;

import io.corebilling.pricing.domain.plan.PlanId;
import io.corebilling.pricing.domain.plan.PriceLine;
import io.corebilling.pricing.domain.rating.RatingId;
import io.corebilling.pricing.domain.rating.RatingStatus;
import io.corebilling.pricing.domain.rating.UsageRating;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
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
