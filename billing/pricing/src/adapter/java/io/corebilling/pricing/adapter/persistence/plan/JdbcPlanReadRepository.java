package io.corebilling.pricing.adapter.persistence.plan;

import io.corebilling.pricing.application.plan.query.PlanReadRepository;
import io.corebilling.pricing.application.plan.query.PlanVersionView;
import io.corebilling.pricing.application.plan.query.PricingPlanView;
import io.corebilling.pricing.domain.plan.BillingCycle;
import io.corebilling.pricing.domain.plan.PlanStatus;
import io.corebilling.pricing.domain.model.PricingModel;
import io.corebilling.pricing.domain.plan.RoundingPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects plans and their versions straight into views; no domain plan is built. */
public class JdbcPlanReadRepository implements PlanReadRepository {

    private static final String PLAN_COLUMNS = "id, code, name, model, currency, status, created_at, retired_at";
    private static final String VERSION_COLUMNS = "plan_id, version, rule::text as rule, billing_cycle, min_charge, max_charge, rounding, valid_from, "
            + "valid_until, created_at, created_by";

    private record PlanRow(
            UUID id, String code, String name, PricingModel model, String currency, PlanStatus status, Instant createdAt, Instant retiredAt) {}

    private record VersionRow(
            UUID planId,
            int version,
            String rule,
            BillingCycle billingCycle,
            Long minCharge,
            Long maxCharge,
            RoundingPolicy rounding,
            Instant validFrom,
            Instant validUntil,
            Instant createdAt,
            String createdBy) {}

    private final JdbcClient jdbc;

    public JdbcPlanReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PricingPlanView> list(PlanStatus status) {
        JdbcClient.StatementSpec statement = status == null
                ? jdbc.sql("select " + PLAN_COLUMNS + " from pricing_plans order by id desc")
                : jdbc.sql("select " + PLAN_COLUMNS + " from pricing_plans where status = ? order by id desc").param(status.name());
        return load(statement);
    }

    @Override
    public Optional<PricingPlanView> findById(UUID id) {
        return load(jdbc.sql("select " + PLAN_COLUMNS + " from pricing_plans where id = ?").param(id)).stream().findFirst();
    }

    private List<PricingPlanView> load(JdbcClient.StatementSpec statement) {
        List<PlanRow> rows = statement.query(PlanRow.class).list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PlanVersionView>> versions = new HashMap<>();
        jdbc.sql("select " + VERSION_COLUMNS + " from pricing_plan_versions where plan_id = any(?) order by plan_id, version")
                .param(rows.stream().map(PlanRow::id).toArray(UUID[]::new))
                .query(VersionRow.class)
                .list()
                .forEach(row -> versions.computeIfAbsent(row.planId(), key -> new ArrayList<>())
                        .add(new PlanVersionView(row.planId(), row.version(), row.rule(), row.billingCycle(), row.minCharge(), row.maxCharge(),
                                row.rounding(), row.validFrom(), row.validUntil(), row.createdAt(), row.createdBy())));
        return rows.stream()
                .map(row -> new PricingPlanView(row.id(), row.code(), row.name(), row.model(), row.currency(), row.status(), row.createdAt(), row.retiredAt(),
                        versions.getOrDefault(row.id(), List.of())))
                .toList();
    }
}
