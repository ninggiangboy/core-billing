package dev.ngb.core_billing.pricing.adapter.rating.persistence;

import static dev.ngb.core_billing_jooq.Tables.USAGE_CHARGES;
import static dev.ngb.core_billing_jooq.Tables.USAGE_RATINGS;

import dev.ngb.core_billing.pricing.application.rating.port.RatingRepository;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlanId;
import dev.ngb.core_billing.pricing.domain.rule.PriceLine;
import dev.ngb.core_billing.pricing.domain.rating.UsageRatingId;
import dev.ngb.core_billing.pricing.domain.rating.RatingStatus;
import dev.ngb.core_billing.pricing.domain.rating.UsageRating;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing_jooq.tables.records.UsageRatingsRecord;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public class JooqRatingRepository implements RatingRepository {

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

    private final DSLContext dsl;
    private final JsonMapper json;

    public JooqRatingRepository(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    @Override
    public boolean addIfAbsent(UsageRating r) {
        return dsl.insertInto(USAGE_RATINGS)
                .set(USAGE_RATINGS.ID, r.id().value())
                .set(USAGE_RATINGS.TENANT_ID, r.tenantId().value())
                .set(USAGE_RATINGS.CUSTOMER_ID, r.customerId().value())
                .set(USAGE_RATINGS.WALLET_ID, r.walletId().value())
                .set(USAGE_RATINGS.PLAN_ID, r.planId().value())
                .set(USAGE_RATINGS.PLAN_VERSION, r.planVersion())
                .set(USAGE_RATINGS.SET_KEY, r.setKey())
                .set(USAGE_RATINGS.ATTEMPT, r.attempt())
                .set(USAGE_RATINGS.PERIOD_START, r.periodStart())
                .set(USAGE_RATINGS.PERIOD_END, r.periodEnd())
                .set(USAGE_RATINGS.USAGE_COUNT, r.usageCount())
                .set(USAGE_RATINGS.AMOUNT, r.amount().minorUnits())
                .set(USAGE_RATINGS.CURRENCY, r.amount().currency().code())
                .set(USAGE_RATINGS.STATUS, r.status().name())
                .set(USAGE_RATINGS.CHARGE_ID, r.chargeId())
                .set(USAGE_RATINGS.FAILURE_REASON, r.failureReason())
                .set(USAGE_RATINGS.BREAKDOWN, JSONB.valueOf(json.writeValueAsString(r.breakdown().stream().map(LineJson::of).toList())))
                .set(USAGE_RATINGS.CREATED_AT, r.createdAt())
                .onConflict(USAGE_RATINGS.TENANT_ID, USAGE_RATINGS.SET_KEY, USAGE_RATINGS.ATTEMPT)
                .doNothing()
                .execute() == 1;
    }

    @Override
    public int link(UsageRatingId ratingId, Collection<UUID> usageEventIds) {
        if (usageEventIds.isEmpty()) {
            return 0;
        }
        Field<UUID> id = DSL.field("unnest({0})", UUID.class, DSL.array(usageEventIds.toArray(UUID[]::new)));
        return dsl.insertInto(USAGE_CHARGES, USAGE_CHARGES.USAGE_EVENT_ID, USAGE_CHARGES.TENANT_ID, USAGE_CHARGES.RATING_ID)
                .select(DSL.select(id, DSL.field("app_tenant_id()", UUID.class), DSL.val(ratingId.value(), USAGE_CHARGES.RATING_ID)))
                .onConflict(USAGE_CHARGES.USAGE_EVENT_ID)
                .doNothing()
                .execute();
    }

    @Override
    public Map<UUID, UsageRatingId> ratedAmong(Collection<UUID> usageEventIds) {
        if (usageEventIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UsageRatingId> found = new HashMap<>();
        dsl.select(USAGE_CHARGES.USAGE_EVENT_ID, USAGE_CHARGES.RATING_ID)
                .from(USAGE_CHARGES)
                .where(USAGE_CHARGES.USAGE_EVENT_ID.in(usageEventIds))
                .forEach(row -> found.put(row.value1(), new UsageRatingId(row.value2())));
        return found;
    }

    @Override
    public int failedAttempts(String setKey) {
        return dsl.fetchCount(USAGE_RATINGS, USAGE_RATINGS.SET_KEY.eq(setKey).and(USAGE_RATINGS.STATUS.eq("FAILED")));
    }

    @Override
    public Optional<UsageRating> findById(UsageRatingId id) {
        return dsl.selectFrom(USAGE_RATINGS)
                .where(USAGE_RATINGS.ID.eq(id.value()))
                .fetchOptional(row -> toDomain(row));
    }

    private UsageRating toDomain(UsageRatingsRecord row) {
        return new UsageRating(
                new UsageRatingId(row.getId()),
                new TenantId(row.getTenantId()),
                new CustomerId(row.getCustomerId()),
                new WalletId(row.getWalletId()),
                new PricingPlanId(row.getPlanId()),
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
                json.readValue(row.getBreakdown().data(), LINES).stream().map(LineJson::toLine).toList(),
                row.getCreatedAt());
    }
}
