---
title: "Core Billing phases 6-7: outbound webhooks, pricing and usage"
date: 2026-11-10
summary: "Built the outbox relay and signed webhook delivery, then pricing plans with immutable versions, usage metering, cost estimation and exactly-once charging from usage."
---

# Core Billing phases 6-7: outbound webhooks, pricing and usage

## What happened

Phases 6 and 7 of the implementation plan are built and verified against real PostgreSQL and Keycloak (418 tests green). They are in the working tree, not committed.

- **Outbox relay and webhooks (phase 6).** A relay claims outbox rows with `skip locked`, publishes each to in-process consumers and marks it delivered in one transaction; a consumer failure rolls back that consumer's writes and a second transaction schedules a retry with backoff, then dead-letters after the limit. The webhook consumer queues one delivery per subscribed endpoint, holding the exact signed body. The sender claims deliveries as leases, sends in parallel outside any transaction, logs every attempt, retries, dead-letters and supports resend. Signing secrets are envelope-encrypted with AES-GCM under a master key from the environment. Outbound calls are SSRF-guarded inside the HTTP client's own DNS resolver, and an architecture rule keeps every other HTTP client out of the module. Event schemas in `contracts/events` are the source of truth for the event catalog and are validated against real payloads.
- **Pricing and usage (phase 7).** Plans with immutable numbered versions, six calculator strategies behind one interface, decimal arithmetic with a single rounding of the total, minimum and maximum charge. Append-only usage with per-event idempotency and an aggregation query. Estimation writes nothing and equals the charge. Charging from usage is exactly-once by construction (a link row per usage event plus a money key derived from the event ids), and reserving from an estimate holds exactly the estimated amount.

## Problems worth remembering

- The first address policy missed private IPv4 addresses hidden in IPv6 forms (`::10.0.0.1`, `64:ff9b::`, 6to4); the table of test addresses caught it.
- `-Werror` rejects the deprecated `execute(ClassicHttpRequest)` of the Apache client; `executeOpen` is the supported call and closing the response without draining it discards the connection, which is what bounds the response size.
- A rating read back from the database differed from the one in memory because `BigDecimal` kept `1E+1` against `10`. Price lines are now stored and compared in one canonical form.
- Postgres cannot see another tenant's rows, so background work runs once per tenant through the existing sweep. That keeps row-level security intact but costs one short transaction per tenant per poll.
- Migration numbers moved: webhooks are `V7` and pricing is `V8` (the plan assumed `V6` and `V7`).
- Several of my own test expectations were wrong arithmetic, not code bugs (a graduated tier total, a balance); worth recomputing by hand before trusting a red test either way.

## Decisions

Recorded in the phase files, and to be written back into `docs/architecture.md` in phase 14:

- `money.api.WalletLookup` is a new read-only edge, so pricing can check a wallet's owner and currency.
- Usage reaches the metric-unit catalog of pricing through its own port, wired in `app-boot`, so the module graph stays one-directional (`pricing -> usage.api`, `pricing -> money.api`).
- Workload-based pricing is a start fee per execution plus a rate for the work; usage thresholds are tier boundaries; hybrid is a base amount plus usage components.
- A rating whose charge fails links no usage and is retried as a new attempt after a top-up.

## Next steps

- Phase 8 will need to store Stripe keys encrypted. The cipher currently implements the webhook module's own port; give payments its own port over the same class rather than sharing a type across contexts.
- Nothing prunes `outbox` rows that are delivered or `event_inbox` rows yet. Add a retention job before the volumes matter.
- Reconsider the per-tenant sweep if the tenant count grows into the thousands.
- Phase 9 (invoices) should use `UsageLookup.totals` for usage lines and `PlanVersion.quote` for the period, not recompute prices.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
