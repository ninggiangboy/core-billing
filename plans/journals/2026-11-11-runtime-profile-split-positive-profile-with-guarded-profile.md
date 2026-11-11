---
title: "Runtime profile split: positive @Profile with guarded profile config"
date: 2026-11-11
summary: "Controllers declare @Profile(\"api\"), jobs @Profile(\"worker\"); spring.profiles.default=api,worker keeps dev/test all-in-one, guarded by on-profile expressions so each profile file applies only to a runtime that runs alone."
---

# Runtime profile split: positive @Profile with guarded profile config

## What happened

Replaced the negative runtime annotations across the app. 22 `@RestController` classes went from `@Profile("!worker & !migrate")` to `@Profile("api")`; the 7 job classes plus `SchedulingConfiguration` went from `@Profile("!api & !migrate")` to `@Profile("worker")`; `PlatformBootstrap` went from `@Profile("!migrate")` to `@Profile({"api", "worker"})`. `MigrateConfiguration` and `ApiExceptionHandler` (`@RestControllerAdvice`) were left alone on purpose.

`application.yml` gained `spring.profiles.default: api,worker` so dev, bootRun and the 43 default-mode `@SpringBootTest` classes keep the all-in-one behaviour. Both profile files then had to guard themselves, because two active profiles load two profile files and the later one wins: `application-api.yml` now carries `spring.config.activate.on-profile: "api & !worker"` and `application-worker.yml` carries `"worker & !api"`. Without the guard the worker file would have won every shared key in dev/test — Flyway off, `lock_timeout` 5s instead of unset, `tenant-parallelism` 8 instead of 1 — and the default-mode integration tests would have failed on a missing schema rather than on anything visible.

## Verification

`./gradlew test` green. The three runtime tests pass unchanged. Added `everyRestControllerServesTheApiRuntime`, `everyScheduledClassRunsInTheWorkerRuntime` and `everyClassInTheJobsPackageRunsInTheWorkerRuntime` to `ArchitectureTest` with subject-count guards, and confirmed all three go red by temporarily breaking one annotation each. Added `theApiProfileAppliesItsOwnSettings` and `theWorkerProfileAppliesItsOwnSettings`, because a broken guard disables a profile file silently: they assert virtual threads plus pool 20 for api, and `tenant-parallelism` 8 plus no virtual threads for worker.

## Decision

Declared runtimes positively, paid for it with an explicit all-in-one default plus two guard lines. The alternative — one dedicated `all` profile that never co-activates `api` and `worker` — needs no guards and no negating config, and stays a two-line change if the guard starts biting.

## Next steps

Commit the work in four slices (config, annotations, ArchUnit, docs) so a bisect can tell config precedence apart from a missing bean. Nothing has been committed yet.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
