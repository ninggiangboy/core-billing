---
title: "Review fixes: bounded rate-limit rows, retryable lock waits, ledger verification handover"
date: 2026-11-11
summary: "The api/worker split review found one security and two operational bugs; all three are fixed and covered by tests, with the full ./gradlew test green."
---

# Review fixes: bounded rate-limit rows, retryable lock waits, ledger verification handover

## What happened

An external review of the eight commits that split the runtime into api, worker and migrate found three issues that
had to be fixed before merge. All three are now fixed, and `./gradlew test` (the whole suite, not just the new
tests) is green.

**1. Rate limiter let an unauthenticated caller create rows without bound.** `Authentication.authenticateApiKey`
spent a bucket before looking the key up, and the bucket key was the caller-supplied key id. Any well-formed
`cbk_<16 hex>_<64 hex>` string created a `rate_limit_buckets` row in its own write transaction.

- The key is now resolved first. Only a request that authenticates spends its own bucket; everything else spends one
  of 1024 shared buckets chosen by the first four bytes of the key id (`Integer.parseUnsignedInt(...) & 1023`). That
  keeps key ids from creating rows and also stops an unknown key id from exhausting a real key's budget.
- `PostgresRateLimiter` derives the TTL from `burst / refill` (minimum 60 s) instead of a flat day, so a bucket is
  expired only once an empty one would have refilled to the full burst. A bucket that never refills keeps the
  one-day TTL, because expiry would hand out a fresh burst.
- `HousekeepingJobs` repeats the bounded `skip locked` delete until a batch comes back short.

**2. A lock wait that timed out was counted as a failed renewal attempt.** `RenewSubscriptionUseCase` wrapped every
`RuntimeException` into `Poisoned`, and the handler recorded a failed attempt: past due, a `subscription.payment_failed`
notice, one hour later, one of five tries gone. With the worker's `lock_timeout = '5s'`, a busy wallet was enough.

- `Causes.isRetryableContention` walks the cause chain for SQLState `55P03` or `40P01` (the driver exception survives
  the jOOQ/Spring translation, and the kernel stays framework-free).
- Renewal breaks the batch without counting or notifying; the period stays due for the next tick.
- The same check keeps `StripeEventProcessor` from spending an event retry and `OutboxRelay` from spending an outbox
  attempt. The relay needed a `CONTENDED` outcome that ends the tick: a contended row stays due, so the loop would
  otherwise pick the same row again (the first version returned `RETRY` and reported 100 failures for one event).

**3. Ledger verification could not hand over, and one failure lost a day.** The cron fired once at 03:00 on every
worker; a worker that lost the lease returned and never tried again, and a run record claimed before the work stayed
after a tenant failed, so that tenant was skipped until tomorrow.

- It is now a ten-minute `fixedDelay` (with an `initialDelay`) that only runs from `billing.jobs.verify-ledger-not-before`
  (03:00 UTC) and decides per tenant by the per-day run record.
- `JobRuns.release` deletes the claim when the tenant's verification throws, so a later tick retries it.
- A dead owner now hands the remaining tenants to another worker within one tick plus the 30-minute lease.

## Problems worth remembering

- `Set.of(...).contains(null)` throws: `SQLException.getSQLState()` can be null, so the helper checks for null before
  the lookup. The first full run failed on exactly that (`CausesTest` runs after the class was already compiled, so
  the failure pointed at code I had already fixed).
- `%` on `Integer.parseUnsignedInt` results is still signed: half the invented key ids landed on negative slots and
  the bound was 2047, not 1024. Masking with `& (n - 1)` is correct only while the count is a power of two, which is
  now part of the constant's comment.
- Test isolation: `aSecondVerificationTheSameDayDoesNoWork` and the new retry test share today's run key. The retry
  test deletes today's claims first; the review's "claim once per day" is a property of the day, not of the test.
- The integration test for contention sets `lock_timeout = '300ms'` instead of the worker's 5 s: same mechanism,
  and the test stays fast.
- `@MockitoSpyBean` is how the verification job was made to fail for one tenant only; `TenantSweep` logs and carries
  on, so the test asserts on `job_runs` rather than on the log.

## Decisions

- Unknown-key traffic is rate limited after the lookup, not before. It costs one indexed read per invented key id, but
  rows stay bounded and a real key cannot be exhausted by a caller who only knows its id. This is the trade the
  review proposed.
- A lock timeout is not a business failure anywhere in the job layer: never a notice, never a dead letter, never a
  spent attempt.
- The daily ledger sweep became a repeated window; the per-day run record, not the schedule, is what makes it
  once-a-day.

## Next steps

- The low findings are untouched: `PlatformBootstrap` still starts serving when it loses the lease, `LockHierarchyTest`
  still checks less than its name says, `connection-init-sql` is still session-scoped (PgBouncer warning missing in
  `docs/runtime-profiles.md`), and `job_runs` is still never pruned.
- No test pushes a real lock timeout through jOOQ to assert the 503 end to end; `RenewalLockContentionIntegrationTest`
  now proves the job half.
- `.github/workflows/billing.yml` was not read, so whether CI runs these tests is still unknown.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
