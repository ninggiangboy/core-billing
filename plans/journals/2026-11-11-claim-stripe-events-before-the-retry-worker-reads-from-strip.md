---
title: Claim Stripe events before the retry worker reads from Stripe
date: 2026-11-11
summary: "Every retry worker claims its batch with skip locked and a claim deadline, so N workers neither prepare the same event nor double count it."
---

# Claim Stripe events before the retry worker reads from Stripe

## What happened

- `V20__stripe_event_claims.sql` adds `stripe_events.claimed_until`; the jOOQ schema was regenerated and committed.
- `StripeEventRepository.due(...)` became `claimDue(now, receivedBefore, lease, limit)`: one statement picks the
  oldest due rows with `for update skip locked` and stamps `claimed_until`. `next_attempt_at` is untouched, so a
  claim never moves the retry schedule.
- `JooqStripeEventRepository.update(...)` clears `claimed_until` when an attempt finishes. Without that, a 30s
  backoff could not come due again until the two-minute claim ran out; two existing retry-loop tests caught it.
- `StripeEventProcessor.Outcome` now reports `attempted`, so a call that found the event final, not due, or already
  settled no longer counts as processed or dead. `RetryStripeEventsUseCase.Round` gained `skipped`.
- Worker scheduler pool 4 -> 9, one thread per scheduled method, so ledger verification and Stripe reconciliation
  cannot queue the outbox relay behind them.
- `PaymentJobs.RECONCILE_TTL` 2h -> 30m: a worker that dies holding the lease no longer blocks reconciliation for
  two hours.
- `docs/runtime-profiles.md` and `docs/concurrency-and-locking.md` record the pool size and the event claim.

## Decision

The all-active model stays: every worker runs every job and PostgreSQL arbitrates. ShedLock was rejected because it
locks a method name, cannot express "once per tenant per day", and would serialise the jobs that currently scale.
Only the Stripe event retry needed a claim of its own, because it reads Stripe before it opens the transaction that
locks the event row.

## Verification

- `./gradlew build` green, including ArchUnit, the jOOQ drift test, the profile tests and the multi-instance
  runtime test.
- New `aClaimedEventIsInvisibleToAnotherWorkerUntilTheClaimPasses`: a second claim takes nothing, the retry
  schedule is unchanged, and an expired claim hands the events back.
- New `severalWorkersRetryingAtOnceNeverTryTheSameEventTwice`: four concurrent rounds over 12 events claim 12
  events in total and write exactly one retry attempt each.
- The first run failed `anEventThatNeverFindsItsPaymentGivesUpAndIsVisible` and
  `aDeadEventCanBeRetriedByAPersonOnceItsCauseIsFixed`; clearing the claim in `update` fixed both.

## Next steps

- Measure outbox and webhook lag at two workers before adding a third.
- `billing.jobs.tenant-parallelism` (8) still bounds database concurrency; keep it below the worker pool when
  raising either number.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
