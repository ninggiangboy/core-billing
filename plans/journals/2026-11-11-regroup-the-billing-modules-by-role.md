---
title: Regroup the billing modules by role
date: 2026-11-11
---

# Regroup the billing modules by role

## What changed

`billing/` held twelve module directories at the same level, so the tree said
nothing about which modules carry business rules. They are now grouped by role,
and every Gradle project path follows its directory:

- `contexts/` — the eight bounded contexts: `platform`, `customer`, `money`,
  `pricing`, `usage`, `invoicing`, `payment`, `webhook`.
- `libs/` — `shared-kernel`, `jooq-schema`.
- `apps/` — `app-boot`.
- `testing/` — `arch-fixtures`.

`:money` is now `:contexts:money`, `:jooq-schema` is `:libs:jooq-schema`, and
`:app-boot` is `:apps:app-boot`. Java packages did not change, so ArchUnit,
Modulith, the jOOQ generated package and `contracts/` were untouched.

## A latent bug the move exposed

`LockHierarchyTest` walked `..` from the module directory and kept only paths
containing `/src/main/`. Every bounded context keeps code in `src/domain`,
`src/application` or `src/adapter`, so the scan had never read a single context
file and passed on an empty set since it was written. It now finds the
repository root once through a shared `RepositoryPaths` helper, walks every
module at any depth, and asserts the scan reached all eight contexts, so it
cannot pass empty again. Verified by appending a `pg_advisory_lock(` call to a
context adapter: the test fails, and passes again after reverting.

The same helper replaced the duplicated root discovery in `OpenApiContractTest`,
`EventSchemas` and `KeycloakFixture`.

## Evidence

- `./gradlew clean build` and `./gradlew build --rerun-tasks`: both green.
- 690 tests, 0 failures, 0 errors — the same count as the baseline taken at
  `54b2b9c` before the move.
- `grep -rn 'project(":<old>")' billing --include='*.kts'` returns 0.
- `.github/workflows/billing.yml` needed no change: it runs `./gradlew build`
  with `working-directory: billing` and no module path.

## Commits

1. `test(architecture)`: locate the repository root instead of counting directories.
2. `refactor(billing)`: group bounded contexts under `contexts/`.
3. `refactor(billing)`: group the shared libraries under `libs/`.
4. `refactor(billing)`: move the boot app under `apps/`.
5. `refactor(billing)`: move the architecture fixtures under `testing/`.
6. `docs`: map the billing module groups and their project paths.

## Notes

- `app-boot/build.gradle.kts` now reads `../../../contracts/events`; the boot jar
  was checked and still contains the event schemas.
- Nothing was pushed. History was not rewritten.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
