---
phase: 2
title: "Runtime profiles"
status: pending
---

# Runtime profiles

## Goal

One jar runs as `api`, `worker` or `migrate`. With no profile, `dev` and `test` keep today's
all-in-one behaviour.

## Work

- Profile constants and gates:
  - Jobs and `@EnableScheduling` load only when neither `api` nor `migrate` is active.
  - Business controllers load only when neither `worker` nor `migrate` is active.
  - Platform bootstrap skips `migrate`.
- `application-api.yml`: Flyway off, app account, API Hikari pool, long `lock_timeout`,
  `idle_in_transaction_session_timeout`, graceful shutdown, readiness and liveness probes.
- `application-worker.yml`: Flyway off, worker Hikari pool, shorter lock timeout, scheduler pool,
  await-termination on shutdown, probes.
- `application-migrate.yml`: owner Flyway, `init-sql` short `lock_timeout`, no web server, jobs and
  bootstrap off, exit after migration.
- Retrying `FlywayMigrationStrategy` for migrate: retry a lock timeout a bounded number of times.
- Keep default `application.yml` behaviour for dev and test.

## Verification

- Context test: `api` has no job beans even with `billing.jobs.enabled=true`.
- Context test: `worker` has no business controller beans and still answers actuator health.
- Migrate profile starts a non-web context, migrates and exits.
- `./gradlew test` green with no active profile.
