---
phase: 14
title: "End-to-end scenarios, hardening and docs"
status: pending
priority: P1
effort: "5d"
dependencies: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]
---

# Phase 14: End-to-end scenarios, hardening and docs

## Goal

Prove all 15 scenarios end to end, close the observability and security gaps, and bring `docs/` in line with what was built.

## Context

Source: [sample-tenant-system.md](../../docs/sample-tenant-system.md) (15 scenarios), [security-and-reliability.md](../../docs/security-and-reliability.md), docs are updated only where user-visible behavior, setup, commands, architecture or decisions changed. Deploy pipelines and infrastructure remain out of scope.

## Files to Create / Modify

- Create: `e2e/` (compose file for the full stack, scenario runner, second-tenant driver) and a CI workflow
- Modify: `billing/` and `sample-tenant/` for tracing, metrics, log redaction, health probes
- Modify: `docs/architecture.md` (decision ledger: add the decisions from this plan, close open decisions 1 to 4), `docs/README.md`, topic documents where behavior changed, root `README.md` with setup commands
- Create: runbooks only for operations that now exist (rebuild job, dead-letter replay, key rotation) under `docs/`

## Tasks & Steps

- [ ] 1. Scenario suite: one automated test per scenario 1 to 15. Tenant A is the Sample Tenant System; Tenant B is a thin external driver using only the public API and API key (scenario 14 cross-tenant reads and writes all fail; scenario 15 same email as independent accounts; scenario 9 one tenant in Direct mode and one in Connect mode).
- [ ] 2. Observability: Micrometer metrics (outbox lag, dead-letter count, webhook delivery success rate, ledger rebuild mismatches, reservation expiry count), OpenTelemetry tracing across Billing and sample tenant, structured logs carrying request id and tenant id, central redaction of secrets and card-like values.
- [ ] 3. Health, readiness and graceful shutdown for both systems; startup fails fast when RLS role configuration is wrong.
- [ ] 4. Security pass: dependency vulnerability scan, secret scan in CI, RLS bypass attempts through the application role, authorization matrix test generated from the Permission catalog, credential and webhook secret redaction tests.
- [ ] 5. Load smoke test for the hot paths (reserve, charge, usage ingest) to size the wallet-lock concern from phase 5 and record numbers in the docs, not as a pass/fail promise.
- [ ] 6. Schedule and document operational jobs: ledger rebuild, reservation expiry, outbox relay, webhook sender, reconciliation, subscription renewal.
- [ ] 7. Documentation reconciliation: read each topic document and correct it against shipped code and executable contracts (OpenAPI, event schemas, migrations), moving any detail now owned by code to a link. Add the plan's assumptions as decisions to the ledger in `docs/architecture.md` including the `pricing → usage.api` read edge, and remove the open decisions that are resolved.
- [ ] 8. Final review: run the code review workflow over the whole branch against the acceptance criteria of `plan.md`.

## Verification

- `e2e` suite passes all 15 scenarios from a clean `docker compose up`.
- `./gradlew build` for `billing/` and `sample-tenant/`, `pnpm -C web typecheck lint test build`, and CI are green.
- Every metric in step 2 appears on a scrape in the e2e run; a redaction test finds no secrets in captured logs.
- A link check over `docs/` finds no broken relative links, and `docs/architecture.md` has no open decision from this plan left.

## Risks and rollback

- The e2e stack is heavy; keep a fast subset (scenarios 1 to 8) for pull requests and run the full set on the main branch.
- Documentation changes are reversible through version control; no data changes happen in this phase.
