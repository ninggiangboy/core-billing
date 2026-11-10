package io.corebilling.pricing.adapter.persistence;

import io.corebilling.pricing.adapter.RuleDto;
import io.corebilling.pricing.application.RatingRepository;
import io.corebilling.pricing.domain.PlanId;
import io.corebilling.pricing.domain.PriceLine;
import io.corebilling.pricing.domain.RatingId;
import io.corebilling.pricing.domain.RatingStatus;
import io.corebilling.pricing.domain.UsageRating;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public class JdbcRatingRepository implements RatingRepository {

    /** The stored form of a breakdown line; amounts are decimal strings so nothing is lost to floating point. */
    private record LineJson(String description, String metric, String quantity, String amount) {
        static LineJson of(PriceLine line) {
            return new LineJson(line.description(), line.metric(), line.quantity() == null ? null : line.quantity().toPlainString(), line.amount().toPlainString());
        }

        PriceLine toLine() {
            return new PriceLine(description, metric, quantity == null ? null : new BigDecimal(quantity), new BigDecimal(amount));
        }
    }

    private static final TypeReference<List<LineJson>> LINES = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcRatingRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean addIfAbsent(UsageRating r) {
        return jdbc.sql("""
                        insert into usage_ratings (id, tenant_id, customer_id, wallet_id, plan_id, plan_version, set_key, attempt, period_start, period_end,
                                                   usage_count, amount, currency, status, charge_id, failure_reason, breakdown, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        on conflict (tenant_id, set_key, attempt) do nothing
                        """)
                .params(r.id().value(), r.tenantId().value(), r.customerId().value(), r.walletId().value(), r.planId().value(), r.planVersion(), r.setKey(),
                        r.attempt(), Timestamp.from(r.periodStart()), Timestamp.from(r.periodEnd()), r.usageCount(), r.amount().minorUnits(),
                        r.amount().currency().code(), r.status().name(), r.chargeId(), r.failureReason(),
                        json.writeValueAsString(r.breakdown().stream().map(LineJson::of).toList()), Timestamp.from(r.createdAt()))
                .update() == 1;
    }

    @Override
    public int link(RatingId ratingId, Collection<UUID> usageEventIds) {
        return jdbc.sql("""
                        insert into usage_charges (usage_event_id, tenant_id, rating_id)
                        select id, app_tenant_id(), ? from unnest(?::uuid[]) as id
                        on conflict (usage_event_id) do nothing
                        """)
                .params(ratingId.value(), usageEventIds.toArray(UUID[]::new))
                .update();
    }

    @Override
    public Map<UUID, RatingId> ratedAmong(Collection<UUID> usageEventIds) {
        if (usageEventIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, RatingId> found = new HashMap<>();
        jdbc.sql("select usage_event_id, rating_id from usage_charges where usage_event_id = any(?)")
                .param(usageEventIds.toArray(UUID[]::new))
                .query((rs, n) -> {
                    found.put(rs.getObject("usage_event_id", UUID.class), new RatingId(rs.getObject("rating_id", UUID.class)));
                    return null;
                })
                .list();
        return found;
    }

    @Override
    public int failedAttempts(String setKey) {
        return jdbc.sql("select count(*) from usage_ratings where set_key = ? and status = 'FAILED'").param(setKey).query(Integer.class).single();
    }

    @Override
    public Optional<UsageRating> findById(RatingId id) {
        return jdbc.sql("""
                        select id, tenant_id, customer_id, wallet_id, plan_id, plan_version, set_key, attempt, period_start, period_end, usage_count, amount,
                               currency, status, charge_id, failure_reason, breakdown::text as breakdown, created_at
                        from usage_ratings where id = ?
                        """)
                .param(id.value())
                .query((rs, n) -> new UsageRating(new RatingId(Rows.uuid(rs, "id")), new TenantId(Rows.uuid(rs, "tenant_id")),
                        new CustomerId(Rows.uuid(rs, "customer_id")), new WalletId(Rows.uuid(rs, "wallet_id")), new PlanId(Rows.uuid(rs, "plan_id")),
                        rs.getInt("plan_version"), rs.getString("set_key"), rs.getInt("attempt"), Rows.instant(rs, "period_start"), Rows.instant(rs, "period_end"),
                        rs.getInt("usage_count"), Money.of(rs.getLong("amount"), Currency.of(rs.getString("currency").strip())),
                        RatingStatus.valueOf(rs.getString("status")), Rows.uuid(rs, "charge_id"), rs.getString("failure_reason"),
                        json.readValue(rs.getString("breakdown"), LINES).stream().map(LineJson::toLine).toList(), Rows.instant(rs, "created_at")))
                .optional();
    }
}
