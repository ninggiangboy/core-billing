# Runtime profiles and deployment

One jar runs in three deployment shapes. `dev` and `test` start with no profile and keep the all-in-one behaviour
they had before the split; production starts one of the profiles below.

| Profile | Serves | Builds | Migrates |
|---|---|---|---|
| `api` (N instances) | HTTP and Stripe webhooks | no job bean, no scheduler | no |
| `worker` (M instances) | actuator probes only | every job, no business controller | no |
| `migrate` (one step) | nothing | no jobs, no bootstrap | Flyway as the table owner, then exits |
| default (no profile) | HTTP and jobs | both | yes |

Deploy in the order `migrate`, `api`, `worker`. The migrate step is the only writer of schema; api and worker connect
as the unprivileged application role with Flyway switched off, so a rolling start never races a migration.

## What each profile owns

- **Jobs.** `worker` owns outbox relay, webhook delivery, subscription renewal, billing cycle close, reservation
  expiry, ledger verification, Stripe retry and reconciliation, Stripe reconciliation and rate-limit cleanup.
  `api` owns none of them. A test asserts both halves.
- **Jobs that must not run twice.** Ledger verification and Stripe reconciliation take a time-bounded lease in
  `job_leases`; a per-day `job_runs` record makes a second ledger verification the same day a no-op. The platform
  bootstrap runs under the same lease, so two instances starting together do not both seed. See
  [concurrency-and-locking.md](concurrency-and-locking.md).
- **Skip-locked jobs.** Outbox, webhook delivery, subscription renewal, billing cycle close and reservation expiry
  already claim one row at a time with `for update skip locked`, so they stay multi-worker safe and are deliberately
  not made singleton.
- **Rate limiting.** API-key limits live in `rate_limit_buckets`, shared by every `api` instance. A key that
  authenticates spends its own bucket; a request that does not spends one of 1024 buckets chosen by key id, so
  invented key ids cannot create unbounded rows or exhaust a real key's budget. A worker removes idle buckets in
  bounded `skip locked` batches, repeating while a batch comes back full.

## Settings per profile

| Setting | `api` | `worker` | `migrate` |
|---|---|---|---|
| Flyway | off | off | on, owner account |
| Hikari pool | `BILLING_API_DB_POOL_SIZE` (20) | `BILLING_WORKER_DB_POOL_SIZE` (10) | 2 |
| Scheduler pool | none | `BILLING_WORKER_SCHEDULER_POOL_SIZE` (4) | none |
| `lock_timeout` | 30s (waiting for a wallet is intended) | 5s (skip the tick, try again) | 3s, bounded retry |
| `idle_in_transaction_session_timeout` | 60s | 60s | default |
| Shutdown | graceful, 30s phase timeout | graceful, scheduler awaits up to 30s | not applicable |
| Probes | `/actuator/health/liveness`, `/actuator/health/readiness` | same | none |
| Flyway `init-sql` | not set | not set | `SET lock_timeout = '3s'` |

The exact values live in `application-api.yml`, `application-worker.yml` and `application-migrate.yml`. They are
starting points measured by the deadlock stress test, not constants to defend: raise the api timeout before raising
the worker's.

## Related

Lock order and coordination rules: [concurrency-and-locking.md](concurrency-and-locking.md). Reliability requirements:
[security-and-reliability.md](security-and-reliability.md).
