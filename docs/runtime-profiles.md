# Runtime profiles and deployment

One jar runs in three deployment shapes. `dev` and `test` start with no profile, which activates `api` and `worker`
together — the all-in-one behaviour from before the split; production starts one of the profiles below.

| Profile | Serves | Builds | Migrates |
|---|---|---|---|
| `api` (N instances) | HTTP and Stripe webhooks | no job bean, no scheduler | no |
| `worker` (M instances) | actuator probes only | every job, no business controller | no |
| `migrate` (one step) | nothing | no jobs, no bootstrap | Flyway as the table owner, then exits |
| default (no profile) | HTTP and jobs | `api` and `worker` together | yes |

Each edge declares its beans positively: a controller carries `@Profile("api")`, a job carries `@Profile("worker")`,
and architecture tests fail when either annotation goes missing. `spring.profiles.default` in `application.yml`
supplies both profiles to a run that sets none.

The two profile files describe a runtime that runs alone, so each guards itself: `application-api.yml` carries
`spring.config.activate.on-profile: api & !worker` and `application-worker.yml` carries `worker & !api`. Without the
guard the dev/test default would load both files, and the worker would win every shared key — Flyway off, a 5s
`lock_timeout` and eight-way tenant parallelism — instead of the base `application.yml` that the default relies on.

Deploy in the order `migrate`, `api`, `worker`. The migrate step is the only writer of schema; api and worker connect
as the unprivileged application role with Flyway switched off, so a rolling start never races a migration.

## What each profile owns

- **Jobs.** `worker` owns outbox relay, webhook delivery, subscription renewal, billing cycle close, reservation
  expiry, ledger verification, Stripe retry, Stripe reconciliation and rate-limit cleanup.
  `api` owns none of them. A test asserts both halves.
- **Jobs that must not run twice.** Ledger verification and Stripe reconciliation take a time-bounded lease in
  `job_leases`; a per-day `job_runs` record makes a second ledger verification the same day a no-op. Ledger
  verification runs on a short fixed delay from 03:00 UTC rather than once at 03:00: a worker that dies mid-round
  hands the remaining tenants to the next tick of another worker once the lease expires, a tenant whose
  verification failed gives its run record back so a later tick tries it again, and a claim left unfinished by a
  worker that died is taken over once it is older than the lease TTL. The platform bootstrap runs under
  the same lease, so two instances starting together do not both seed. See
  [concurrency-and-locking.md](concurrency-and-locking.md).
- **Skip-locked jobs.** Outbox, webhook delivery, subscription renewal, billing cycle close, reservation expiry and
  Stripe event retry claim their rows with `for update skip locked`, so they stay multi-worker safe and are
  deliberately not made singleton. The Stripe event retry also takes a claim deadline of its own
  (`stripe_events.claimed_until`), because it reads from Stripe before it opens the transaction that locks the row:
  a claimed event is invisible to another worker until that deadline passes, and a worker that dies mid-attempt
  hands it back instead of losing it.
- **Rate limiting.** API-key limits live in `rate_limit_buckets`, shared by every `api` instance. A key that
  authenticates spends its own bucket; a request that does not spends one of 1024 buckets chosen by key id, so
  invented key ids cannot create unbounded rows or exhaust a real key's budget. A worker removes idle buckets in
  bounded `skip locked` batches, repeating while a batch comes back full.
- **Threading.** `api` enables virtual threads, so a wait for Stripe or Keycloak does not occupy a request thread,
  and pairs that with a 3s Hikari `connection-timeout`: the pool stays at 20, so a request that cannot get a
  connection is refused with a 503 and `Retry-After: 1` instead of queueing for 30s. `worker` deliberately stays on
  platform threads, because enabling virtual threads there would replace the nine-thread scheduler with
  `SimpleAsyncTaskScheduler` and drop `spring.task.scheduling.pool.size`, which the worker relies on. The pool has one
  thread per scheduled method, so a long round cannot hold up the pollers behind it. Jobs bound their own concurrency
  with `billing.jobs.tenant-parallelism`, not with the scheduler's thread count.

## Settings per profile

| Setting | `api` | `worker` | `migrate` |
|---|---|---|---|
| Flyway | off | off | on, owner account |
| Hikari pool | `BILLING_API_DB_POOL_SIZE` (20) | `BILLING_WORKER_DB_POOL_SIZE` (10) | 2 |
| Hikari `connection-timeout` | 3s | default | default |
| Virtual threads | enabled | off (platform threads) | off |
| Tenant parallelism | not applicable | `BILLING_WORKER_TENANT_PARALLELISM` (8), below the pool | not applicable |
| Scheduler pool | none | `BILLING_WORKER_SCHEDULER_POOL_SIZE` (9, one per scheduled method) | none |
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
