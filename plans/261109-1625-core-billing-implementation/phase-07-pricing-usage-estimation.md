---
phase: 7
title: "Pricing, usage and cost estimation"
status: completed
priority: P1
effort: "6d"
dependencies: [5]
---

# Phase 7: Pricing, usage and cost estimation

## Goal

Define Pricing Plans for all six models, record idempotent Usage, estimate cost before execution, and charge or reserve from those results through `money.api`.

## Context

Source: [pricing-and-usage.md](../../docs/pricing-and-usage.md). `pricing` calls `money.api` synchronously; `money` never calls back. Charge from usage reads quantities through `usage.api` (read-only), an extra edge recorded in the architecture ledger in phase 14.

## Files to Create / Modify

- Create: `billing/pricing/` (`PricingPlan` with immutable versions, calculator strategies, estimation and charge-from-usage use cases, REST)
- Create: `billing/usage/` (`UsageEvent`, ingestion and aggregation use cases, REST, public `api` for quantity lookup)
- Create: Flyway `V8__pricing_usage.sql`; event schemas for new events in `contracts/events/`

## Tasks & Steps

- [x] 1. `PricingPlan` with versions: billing unit, unit price, tiers, usage thresholds, billing cycle, minimum and maximum charge, rounding policy, currency, validity period. A new version never changes charges already made; a charge records the plan version it used.
- [x] 2. Calculator strategies behind one interface: time-based (duration times unit price), usage-based with tiers (graduated by default, volume as an option), workload-based, event-based, subscription (fixed amount per cycle), hybrid (base plus components). Intermediate math uses `BigDecimal`; conversion to minor units happens once, with the plan's rounding policy.
- [x] 3. Cost estimation API: pure calculation from plan version and quantities, returns amount, breakdown and currency, writes nothing.
- [x] 4. `UsageEvent`: tenant, customer, metric, quantity, unit, occurred_at, idempotency key, source trace (tenant system, resource reference, source event id). Append-only; `unique (tenant_id, idempotency_key)`; indexed for aggregation by customer, metric and period.
- [x] 5. Usage ingestion REST: single and batch, idempotent per event, validates metric and unit against the plan's billing unit; supports recording usage over time.
- [x] 6. Aggregation query: quantity by customer, metric and period, for rating and for invoices.
- [x] 7. Charge from usage: load plan version, read quantities via `usage.api`, compute amount, call `money.api` charge with a deterministic idempotency key derived from the usage ids; a link table `usage_charges(usage_event_id unique)` prevents rating one usage event twice.
- [x] 8. Reserve from estimate: compute the estimate and call `money.api` reserve with the estimate as the held amount.
- [x] 9. Plan REST: Tenant administrators create, version and retire plans; Tenant Systems list active plans.
- [x] 10. Table-driven calculator tests covering every model, tier boundaries, min and max charge and each rounding policy.

## Verification

- Table-driven calculator tests pass without a Spring context.
- Posting the same usage event twice stores one row; charging the same usage twice produces one Ledger entry.
- Adding a second pricing strategy in a test touches only `pricing.domain`, shown by `money` and `usage` compiling unchanged.
- Estimation never writes to the database (test asserts no row count change).
- Architecture rules pass, including no import of `money` internals from `pricing`.

## Risks and rollback

- Rounding disagreements between estimate and final charge: both use the same calculator and rounding policy, and a test asserts an estimate equals the charge for identical inputs.
- High usage volume could strain a single table; the index and batch endpoint are the first lever, extraction of `usage` is the documented later step.

## Deviations and results

- **Migration number.** `V8__pricing_usage.sql`, because the webhook schema took `V7`.
- **Wallet lookup.** `money.api.WalletLookup` (a read-only summary: owner, currency, mode) was added so charging and reserving can refuse a wallet that belongs to another customer or is in another currency. It is the only change to the money module.
- **Metric units.** The plan's billing unit is a registry (`pricing_metrics`): the first plan that prices a metric fixes its unit, a conflicting plan is refused, and usage ingestion validates metric and unit against it. Usage asks through its own `MetricUnits` port; the wiring layer answers it from `pricing.api.MetricCatalog`, so `usage` does not depend on `pricing` and the only edge is the read-only `pricing -> usage.api` one the plan describes.
- **Prices** are decimals in minor units (not integers), so fractions of a cent price correctly. Total rounding happens once.
- **Workload-based** is a start fee per execution plus a rate for the work done, because that is what distinguishes it from usage-based pricing. **Hybrid** is a base amount per cycle plus usage components. **Usage thresholds** are the tier boundaries, with a zero-priced first tier as an allowance.
- **Charge from usage** is idempotent by construction (no header): a link table makes each usage event rate once, the money key is a digest of the event ids, and a failed charge records an attempt without linking so it can be retried after a top-up. A zero amount is accounted for without a charge. Limits: 10,000 events per rating, 500 per batch, six decimal places.
- **Events** `pricing-plan.version-published`, `pricing-plan.retired` and `usage.rated`, each with a schema in `contracts/events`. Permissions: `pricing:read`, `pricing:manage` (administrators only), `usage:record`, `usage:read`.
- **Verification.** Table-driven calculator tests with no Spring context (every model, tier boundaries, limits, every rounding policy); a custom strategy priced through the unchanged pipeline plus an architecture rule that money, usage, webhook and customer never reference pricing; a test that estimating changes no row in any table; estimate equals charge; concurrent identical charges produce one ledger entry; a fault injected after the money moved rolls back the charge, rating and events.
