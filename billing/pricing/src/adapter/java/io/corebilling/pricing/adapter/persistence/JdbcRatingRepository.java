package io.corebilling.pricing.adapter.persistence;

import io.corebilling.pricing.application.rating.port.RatingRepository;
import io.corebilling.pricing.domain.rating.RatingId;
import io.corebilling.pricing.domain.rating.UsageRating;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

public class JdbcRatingRepository implements RatingRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcRatingRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean addIfAbsent(UsageRating r) {
        UsageRatingRow row = UsageRatingRow.from(r, json);
        return jdbc.sql("""
                        insert into usage_ratings (id, tenant_id, customer_id, wallet_id, plan_id, plan_version, set_key, attempt, period_start, period_end,
                                                   usage_count, amount, currency, status, charge_id, failure_reason, breakdown, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        on conflict (tenant_id, set_key, attempt) do nothing
                        """)
                .params(row.id(), row.tenantId(), row.customerId(), row.walletId(), row.planId(), row.planVersion(), row.setKey(), row.attempt(),
                        Timestamp.from(row.periodStart()), Timestamp.from(row.periodEnd()), row.usageCount(), row.amount(), row.currency(), row.status(),
                        row.chargeId(), row.failureReason(), row.breakdown(), Timestamp.from(row.createdAt()))
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
                .query(RatedUsageRow.class)
                .list()
                .forEach(row -> found.put(row.usageEventId(), new RatingId(row.ratingId())));
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
                .query(UsageRatingRow.class)
                .optional()
                .map(row -> row.toDomain(json));
    }
}
