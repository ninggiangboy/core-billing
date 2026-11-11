---
title: "Review follow-up: contention is not an incident and no row blocks its neighbours"
date: 2026-11-11
summary: "Fixed the two medium issues the first review round left behind: reconciliation no longer records lock timeouts as incidents, renewal/outbox pass over a contended row instead of stopping, and a ledger-verify claim abandoned by a dead worker is taken over."
---

# Review follow-up: contention is not an incident and no row blocks its neighbours

## What happened

A second pass over the previous three review fixes found two medium issues caused by those fixes, plus one low-medium
one. All three are now fixed and `./gradlew test` (the whole suite) is green.

**A. Reconciliation turned a lock timeout into an incident.** `ReconcileStripeUseCase.failed()` caught every
`RuntimeException`, recorded a `RECONCILIATION_MISMATCH` incident and counted a mismatch. With the worker's
`lock_timeout = '5s'`, a busy payment or wallet row was enough to page a person. The contention check now lives in
`failed()`, the one place that records a failure, so both the checkout and refund items behave the same way: a lock
wait is skipped, nothing is written, and the next run examines it again. A new test holds the payment row from another
connection and asserts `Report(1, 0, 0)` with no incident, then that the next run corrects the payment.

**B. A contended row could starve the rows behind it.** Renewal broke the batch on contention and the outbox relay
stopped on `CONTENDED`. Because the contended row keeps its place at the head of `(next_renewal_at, id)` /
`(next_attempt_at, id)`, every tick hit the same row and stopped. Both loops now keep a set of contended ids for the
rest of the tick and exclude them from the next query, so rows behind the busy one still move. This matters for the
outbox too because the outbox order is global, not per aggregate. Tests publish a contended event ahead of a healthy
one and assert the healthy event is delivered while the contended one stays due with no attempt spent.

**C. A worker killed mid-verification lost that tenant for the day.** The per-day `job_runs` claim was only given back
when the same process caught an exception. `job_runs` now has `finished_at`; `JobRuns.claim` takes over a claim only
when it is unfinished and older than the job's TTL, and `MoneyJobs` marks the run finished on success. The jOOQ schema
was regenerated from the new migration, so the drift test stays green. Tests cover a stale unfinished claim being
taken over, a finished claim never being repeated, and a fresh claim still being owned.

## Decision

- Detect contention where the error is recorded, not at each call site. That is why the check moved into
  `ReconcileStripeUseCase.failed()` rather than being repeated in both `catch` blocks.
- Skip a contended row for the remainder of the tick instead of ending the tick. The set is bounded by the batch size,
  so no cursor or extra query state is needed.
- Use `finished_at` rather than a pure TTL reclaim. Reclaiming any old claim would re-verify tenants that already
  finished and double-count `billing.ledger.mismatch`.
- No logging in the payment application layer: slf4j is not on that source set's classpath and the layer keeps
  logging out. The skipped item is visible in the report (checked, not corrected) and the next run retries it.

## Next steps

- Still unverified from the first review: an end-to-end test that a real jOOQ lock timeout surfaces as 503 from the
  API, and whether `.github/workflows/billing.yml` runs these integration tests.
- Low-priority items left as-is: the verify job still sweeps every tenant every 10 minutes after the day is done; a
  rate-limited request still costs the credential read; `HousekeepingJobs` would loop forever if `cleanup-batch` were
  configured to zero.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
