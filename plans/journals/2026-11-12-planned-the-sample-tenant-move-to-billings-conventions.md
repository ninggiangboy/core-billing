---
title: "Planned the sample-tenant move to billing's conventions"
date: 2026-11-12
summary: "Reviewed sample-tenant against billing and wrote a 9-phase plan: feature-role layout, jOOQ, command/query split, ArchUnit rules, integration tests."
---

# Planned the sample-tenant move to billing's conventions

## What happened
- Reviewed `sample-tenant/` against `billing/`, then re-reviewed after the shared `core/` extraction. The core extraction is sound: core, sample-tenant and billing architecture tests all passed. One gap: core's own tests run in no CI workflow.
- The sample still had a flat package tree, Spring Data JDBC Rows mixed with `JdbcTemplate`, reads returning domain aggregates, 3 ArchUnit rules against billing's 33, and one real cross-module import (`CustomerAccountListener` importing `identity.application` and `identity.domain`).
- Wrote `plans/261112-0340-sample-tenant-billing-conventions` (deep mode, 9 phases, about 11 days estimated).

## Decisions
- The sample moves to jOOQ with its own `libs:jooq-schema` and adopts billing's command/query split. The user confirmed both.
- `resource` owns `execution_queue`; `execution` reaches it through a new `resource.api.execution.ExecutionQueue`. The user chose this over the recommended co-ownership. `execution` keeps no persistence, and `CreateOrderUseCase` opens core's `UnitOfWork` around the order and queue writes.
- Keep module `billing-client`: renaming it to `billing` would make the sample's own `checkNoBillingDependency` flag its project path. Only `webhook-receiver` becomes `webhook`.
- Copy billing's generic ArchUnit rules into the sample instead of extracting a shared library into `core/`. Spring Modulith verification is included.
- HTTP characterization tests (phase 5) run on the old persistence before any module migration, so they act as the safety net for views and jOOQ statements.

## Next steps
- Execute with `/ak-cook` on the plan, starting at phase 1.
- Still open and outside the plan: add a `core.yml` workflow so `core/` tests run in CI. No code was changed in this session.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
