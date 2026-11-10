package dev.ngb.core_billing.pricing.adapter.persistence.rating;

import dev.ngb.core_billing.pricing.application.rating.query.PriceLineView;
import dev.ngb.core_billing.pricing.application.rating.query.RatingReadRepository;
import dev.ngb.core_billing.pricing.application.rating.query.UsageRatingView;
import dev.ngb.core_billing.pricing.domain.rating.RatingStatus;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Selects a stored rating straight into a view; the breakdown JSON is decoded into view lines. */
public class JdbcRatingReadRepository implements RatingReadRepository {

    private record LineJson(String description, String metric, String quantity, String amount) {

        PriceLineView toView() {
            return new PriceLineView(description, metric, quantity == null ? null : new BigDecimal(quantity), new BigDecimal(amount));
        }
    }

    private record RatingRow(
            UUID id,
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
            Instant createdAt) {}

    private static final TypeReference<List<LineJson>> LINES = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcRatingReadRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<UsageRatingView> findById(UUID id) {
        return jdbc.sql("""
                        select id, customer_id, wallet_id, plan_id, plan_version, set_key, attempt, period_start, period_end, usage_count, amount,
                               currency, status, charge_id, failure_reason, breakdown::text as breakdown, created_at
                        from usage_ratings where id = ?
                        """)
                .param(id)
                .query(RatingRow.class)
                .optional()
                .map(row -> new UsageRatingView(
                        row.id(),
                        new CustomerId(row.customerId()),
                        new WalletId(row.walletId()),
                        row.planId(),
                        row.planVersion(),
                        row.setKey(),
                        row.attempt(),
                        row.periodStart(),
                        row.periodEnd(),
                        row.usageCount(),
                        Money.of(row.amount(), Currency.of(row.currency().strip())),
                        RatingStatus.valueOf(row.status()),
                        row.chargeId(),
                        row.failureReason(),
                        json.readValue(row.breakdown(), LINES).stream().map(LineJson::toView).toList(),
                        row.createdAt()));
    }
}
