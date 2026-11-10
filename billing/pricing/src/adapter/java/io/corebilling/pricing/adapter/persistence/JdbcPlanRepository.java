package io.corebilling.pricing.adapter.persistence;

import io.corebilling.pricing.application.plan.port.PlanRepository;
import io.corebilling.pricing.domain.plan.PlanId;
import io.corebilling.pricing.domain.plan.PlanVersion;
import io.corebilling.pricing.domain.plan.PricingPlan;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

public class JdbcPlanRepository implements PlanRepository {

    private static final String PLAN_COLUMNS = "id, tenant_id, code, name, model, currency, status, created_at, retired_at";
    private static final String VERSION_COLUMNS = "plan_id, version, tenant_id, rule::text as rule, billing_cycle, min_charge, max_charge, rounding, "
            + "valid_from, valid_until, created_at, created_by";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcPlanRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void add(PricingPlan plan) {
        PricingPlanRow row = PricingPlanRow.from(plan);
        jdbc.sql("insert into pricing_plans (" + PLAN_COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(row.id(), row.tenantId(), row.code(), row.name(), row.model(), row.currency(), row.status(),
                        Timestamp.from(row.createdAt()), row.retiredAt() == null ? null : Timestamp.from(row.retiredAt()))
                .update();
        plan.versions().forEach(this::addVersion);
    }

    @Override
    public void addVersion(PlanVersion version) {
        PlanVersionRow row = PlanVersionRow.from(version, json);
        jdbc.sql("insert into pricing_plan_versions (plan_id, version, tenant_id, rule, billing_cycle, min_charge, max_charge, rounding, valid_from, "
                        + "valid_until, created_at, created_by) values (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(row.planId(), row.version(), row.tenantId(), row.rule(), row.billingCycle(), row.minCharge(), row.maxCharge(), row.rounding(),
                        Timestamp.from(row.validFrom()), row.validUntil() == null ? null : Timestamp.from(row.validUntil()), Timestamp.from(row.createdAt()),
                        row.createdBy())
                .update();
    }

    @Override
    public void update(PricingPlan plan) {
        int updated = jdbc.sql("update pricing_plans set status = ?, retired_at = ? where id = ?")
                .params(plan.status().name(), plan.retiredAt() == null ? null : Timestamp.from(plan.retiredAt()), plan.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("pricing plan " + plan.id() + " was not updated");
        }
    }

    @Override
    public Optional<PricingPlan> findById(PlanId id) {
        return load(jdbc.sql("select " + PLAN_COLUMNS + " from pricing_plans where id = ?").param(id.value())).stream().findFirst();
    }

    @Override
    public Optional<PricingPlan> lockById(PlanId id) {
        return load(jdbc.sql("select " + PLAN_COLUMNS + " from pricing_plans where id = ? for update").param(id.value())).stream().findFirst();
    }

    @Override
    public Optional<PricingPlan> findByCode(String code) {
        return load(jdbc.sql("select " + PLAN_COLUMNS + " from pricing_plans where code = ?").param(code)).stream().findFirst();
    }

    private List<PricingPlan> load(JdbcClient.StatementSpec statement) {
        List<PricingPlanRow> rows = statement.query(PricingPlanRow.class).list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, PricingPlanRow> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.id(), row));
        Map<UUID, List<PlanVersion>> versions = new HashMap<>();
        jdbc.sql("select " + VERSION_COLUMNS + " from pricing_plan_versions where plan_id = any(?) order by plan_id, version")
                .param(byId.keySet().toArray(UUID[]::new))
                .query(PlanVersionRow.class)
                .list()
                .forEach(row -> versions.computeIfAbsent(row.planId(), key -> new ArrayList<>())
                        .add(row.toDomain(byId.get(row.planId()).pricingModel(), json)));
        return rows.stream().map(row -> row.toDomain(versions.getOrDefault(row.id(), List.of()))).toList();
    }
}
