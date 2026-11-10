package io.corebilling.pricing.adapter.persistence;

import io.corebilling.pricing.adapter.RuleDto;
import io.corebilling.pricing.application.PlanRepository;
import io.corebilling.pricing.domain.BillingCycle;
import io.corebilling.pricing.domain.PlanId;
import io.corebilling.pricing.domain.PlanStatus;
import io.corebilling.pricing.domain.PlanVersion;
import io.corebilling.pricing.domain.PricingModel;
import io.corebilling.pricing.domain.PricingPlan;
import io.corebilling.pricing.domain.RoundingPolicy;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.TenantId;
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

    private record PlanRow(UUID id, UUID tenantId, String code, String name, String model, String currency, String status, java.time.Instant createdAt,
            java.time.Instant retiredAt) {}

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcPlanRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void add(PricingPlan plan) {
        jdbc.sql("insert into pricing_plans (" + PLAN_COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(plan.id().value(), plan.tenantId().value(), plan.code(), plan.name(), plan.model().name(), plan.currency().code(), plan.status().name(),
                        Timestamp.from(plan.createdAt()), plan.retiredAt() == null ? null : Timestamp.from(plan.retiredAt()))
                .update();
        plan.versions().forEach(this::addVersion);
    }

    @Override
    public void addVersion(PlanVersion v) {
        jdbc.sql("insert into pricing_plan_versions (plan_id, version, tenant_id, rule, billing_cycle, min_charge, max_charge, rounding, valid_from, "
                        + "valid_until, created_at, created_by) values (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(v.planId().value(), v.number(), v.tenantId().value(), json.writeValueAsString(RuleDto.of(v.rule())),
                        v.billingCycle() == null ? null : v.billingCycle().name(), v.minCharge(), v.maxCharge(), v.rounding().name(),
                        Timestamp.from(v.validFrom()), v.validUntil() == null ? null : Timestamp.from(v.validUntil()), Timestamp.from(v.createdAt()),
                        v.createdBy())
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

    @Override
    public List<PricingPlan> list(PlanStatus status) {
        var statement = status == null
                ? jdbc.sql("select " + PLAN_COLUMNS + " from pricing_plans order by id desc")
                : jdbc.sql("select " + PLAN_COLUMNS + " from pricing_plans where status = ? order by id desc").param(status.name());
        return load(statement);
    }

    private List<PricingPlan> load(JdbcClient.StatementSpec statement) {
        List<PlanRow> rows = statement.query((rs, n) -> new PlanRow(Rows.uuid(rs, "id"), Rows.uuid(rs, "tenant_id"), rs.getString("code"), rs.getString("name"),
                rs.getString("model"), rs.getString("currency"), rs.getString("status"), Rows.instant(rs, "created_at"), Rows.instant(rs, "retired_at"))).list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PlanVersion>> versions = new HashMap<>();
        jdbc.sql("select " + VERSION_COLUMNS + " from pricing_plan_versions where plan_id = any(?) order by plan_id, version")
                .param(rows.stream().map(PlanRow::id).toArray(UUID[]::new))
                .query((rs, n) -> {
                    UUID planId = Rows.uuid(rs, "plan_id");
                    PricingModel model = rows.stream().filter(r -> r.id().equals(planId)).findFirst().map(r -> PricingModel.valueOf(r.model())).orElseThrow();
                    String cycle = rs.getString("billing_cycle");
                    RuleDto rule = json.readValue(rs.getString("rule"), RuleDto.class);
                    return new PlanVersion(new PlanId(planId), new TenantId(Rows.uuid(rs, "tenant_id")), rs.getInt("version"),
                            rule.toRule(model), cycle == null ? null : BillingCycle.valueOf(cycle), Rows.nullableLong(rs, "min_charge"),
                            Rows.nullableLong(rs, "max_charge"), RoundingPolicy.valueOf(rs.getString("rounding")), Rows.instant(rs, "valid_from"),
                            Rows.instant(rs, "valid_until"), Rows.instant(rs, "created_at"), rs.getString("created_by"));
                })
                .list()
                .forEach(v -> versions.computeIfAbsent(v.planId().value(), k -> new ArrayList<>()).add(v));
        return rows.stream().map(r -> PricingPlan.restore(new PlanId(r.id()), new TenantId(r.tenantId()), r.code(), r.name(), PricingModel.valueOf(r.model()),
                Currency.of(r.currency().strip()), PlanStatus.valueOf(r.status()), r.createdAt(), r.retiredAt(), versions.getOrDefault(r.id(), List.of()))).toList();
    }
}
