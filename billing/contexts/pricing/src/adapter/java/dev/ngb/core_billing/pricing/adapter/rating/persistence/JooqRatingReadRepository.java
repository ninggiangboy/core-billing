package dev.ngb.core_billing.pricing.adapter.rating.persistence;

import static dev.ngb.core_billing_jooq.Tables.USAGE_RATINGS;

import dev.ngb.core_billing.pricing.application.rating.query.PriceLineView;
import dev.ngb.core_billing.pricing.application.rating.query.RatingReadRepository;
import dev.ngb.core_billing.pricing.application.rating.query.UsageRatingView;
import dev.ngb.core_billing.pricing.domain.rating.RatingStatus;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing_jooq.tables.records.UsageRatingsRecord;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Selects a stored rating straight into a view; the breakdown JSON is decoded into view lines. */
public class JooqRatingReadRepository implements RatingReadRepository {

    private record LineJson(String description, String metric, String quantity, String amount) {

        PriceLineView toView() {
            return new PriceLineView(description, metric, quantity == null ? null : new BigDecimal(quantity), new BigDecimal(amount));
        }
    }

    private static final TypeReference<List<LineJson>> LINES = new TypeReference<>() {};

    private final DSLContext dsl;
    private final JsonMapper json;

    public JooqRatingReadRepository(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    @Override
    public Optional<UsageRatingView> findById(UUID id) {
        return dsl.selectFrom(USAGE_RATINGS)
                .where(USAGE_RATINGS.ID.eq(id))
                .fetchOptional(row -> toView(row));
    }

    private UsageRatingView toView(UsageRatingsRecord row) {
        return new UsageRatingView(
                row.getId(),
                new CustomerId(row.getCustomerId()),
                new WalletId(row.getWalletId()),
                row.getPlanId(),
                row.getPlanVersion(),
                row.getSetKey(),
                row.getAttempt(),
                row.getPeriodStart(),
                row.getPeriodEnd(),
                row.getUsageCount(),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                RatingStatus.valueOf(row.getStatus()),
                row.getChargeId(),
                row.getFailureReason(),
                json.readValue(row.getBreakdown().data(), LINES).stream().map(LineJson::toView).toList(),
                row.getCreatedAt());
    }
}
