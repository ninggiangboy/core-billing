---
title: Rename the Java root package to dev.ngb.core_billing
date: 2026-11-10
summary: Moved every billing module from io.corebilling to dev.ngb.core_billing and set the Gradle group to dev.ngb; full build green with the same 551 tests.
---

# Rename the Java root package to dev.ngb.core_billing

## What happened

The Java root package was `io.corebilling`, a namespace the project does not own. It was renamed to `dev.ngb.core_billing` because `core-billing` (matching the repo name) is not a legal Java package identifier: hyphens are not allowed in package segments.

Scope measured before editing: 541 Java files across 11 Gradle modules and 36 per-layer source-set directories (`api`, `domain`, `application`, `adapter`, `main`, `test`), plus one Gradle convention line, four package strings in the architecture tests, and one escaped regex literal.

Execution:

- Moved all 36 directories with `git mv` so history follows the files.
- Replaced content with `s/io\.corebilling/dev.ngb.core_billing/g` over `*.java` and `*.kts` only. This pattern cannot touch `corebilling_app` DB roles, the Keycloak realm, `POSTGRES_DB`, or the `corebilling.io` event `$id` values.
- Handled one extra case the plain pattern missed: a regex literal in `AccessControlHttpIntegrationTest` written as `io\\.corebilling`, replaced with escaped dots preserved (`dev\\.ngb\\.core_billing`).
- Added `allprojects { group = "dev.ngb" }` to `billing/build.gradle.kts`. Gradle does not inherit `group` from the root project, so setting it on the root alone would have left 11 subprojects with an empty group.
- Updated `mainClass` in `build-logic/src/main/kotlin/boot-conventions.gradle.kts`.

## Decision

Verified rather than assumed. `./gradlew clean build` was green before and after the rename with an identical test count (312 in `app-boot`, 239 in the other modules, 0 failures). `Start-Class` in the bootJar manifest was read directly because Spring Boot does not resolve `mainClass` at build time: a wrong value still produces a successful build and only breaks the jar. Modulith still discovers all eight modules plus `sharedkernel` because they keep a shared root package, and the architecture rules take the root as a parameter, so no rule changed meaning. `arch-fixtures` keeps its separate `archfixtures` package so the meta-test still proves each rule has teeth.

Out of scope by decision: database name and role, Keycloak realm, `docker-compose.yml`, `.env.example`, event schema `$id` URLs, `contracts/`, and the historical text in `plans/*.md`. Those are not Java packages, and changing them would break deployed state or published contracts.

## Next steps

- `docs/architecture.md` records the new root package and the Gradle group.
- If the deciding factor changes (a published artifact, a second repo sharing the `dev.ngb` namespace), the package name becomes expensive to change. `dev.ngb.core_billing` is a workaround for the hyphen, not a considered naming choice.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
