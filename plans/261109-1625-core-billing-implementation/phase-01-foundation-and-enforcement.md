---
phase: 1
title: "Foundation and enforcement"
status: completed
priority: P1
effort: "3d"
dependencies: []
---

# Phase 1: Foundation and enforcement

## Goal

Create the repository layout, the Gradle build and the architecture rules so that a layer, purity or cross-context violation fails the build before any business code exists.

## Context

Source: [architecture.md](../../docs/architecture.md) (module boundaries), `plans/reports/advise-261109-2248-ddd-clean-architecture.md` (enforcement plan and the "Unverified" list). Two points the spike must settle: classpath-level purity with `domain`, `application` and `adapter` in one Gradle module, and Spring Modulith discovery across Gradle modules.

## Files to Create / Modify

- Create: `billing/settings.gradle.kts`, `billing/build.gradle.kts`, `billing/gradle/libs.versions.toml`, Gradle wrapper
- Create: `billing/build-logic/` with convention plugins `java-conventions`, `layered-module-conventions`, `boot-conventions`
- Create: `billing/shared-kernel/` and `billing/app-boot/` (only these two modules now)
- Create: `billing/arch-fixtures/` (deliberately violating classes, never on the application classpath)
- Create: `billing/app-boot/src/test/java/.../architecture/` (ArchUnit rules, Modulith verification, meta-test)
- Create: `docker-compose.yml` (PostgreSQL for local dev), `.github/workflows/billing.yml`, `.gitignore`, root `README.md` pointer to `docs/`
- Modify: `docs/architecture.md` is changed in phase 14, not here

## Tasks & Steps

- [x] 1. Create the root layout `billing/`, `sample-tenant/`, `web/`, `contracts/` (empty directories get a short `README.md` only if needed to keep them tracked).
- [x] 2. Pin versions: check current stable Spring Boot, Spring Modulith, Spring Data JDBC, Flyway, ArchUnit, Testcontainers and the JDK LTS for mutual compatibility; record them in `libs.versions.toml`. Do not copy versions from memory.
- [x] 3. Spike the layered module convention: three source sets per module (`domain` with no dependencies, `application` depending on `domain`, `adapter` depending on both plus Spring and JDBC). Compile a throwaway class in `domain` that imports `org.springframework` and confirm compilation fails.
- [x] 4. Spike Spring Modulith `ApplicationModules.verify()` with two Gradle modules sharing the root package `io.corebilling`. Record the outcome in the phase notes: discovery works, or Modulith is limited to event documentation and ArchUnit owns boundaries.
- [x] 5. Choose the outcome of steps 3 and 4 and finish `layered-module-conventions` accordingly. If source sets are rejected, add a Gradle task that greps `*/domain/**` and `*/application/**` for `org.springframework`, `jakarta.persistence`, `com.fasterxml.jackson` and fails on a hit.
- [x] 6. Build `shared-kernel` with at most ten types: `TenantId`, `CustomerId`, `WalletId`, `Money`, `Currency`, `IdGenerator`, `Clock`, `UnitOfWork`, `DomainEvent`, `EventPublisher`. `Money` holds `long` minor units and a `Currency` with its ISO 4217 exponent; it rejects mixed-currency arithmetic and overflow.
- [x] 7. Write ArchUnit rules (six to eight, no more): layer direction, `domain` and `application` purity, no cross-context import outside `..api..`, persistence row types only in `adapter..persistence..`, every use class under `money..application..` depends on `UnitOfWork`, `api` package naming, no JPA anywhere, `shared-kernel` has no module dependency.
- [x] 8. Wire Spring Modulith `verify()` and event documentation output into the build (or the reduced role chosen in step 4).
- [x] 9. Write the meta-test: run the same rule set against `arch-fixtures` and assert that each rule reports a violation.
- [x] 10. Add Testcontainers PostgreSQL support and Flyway wiring in `app-boot` with an empty baseline migration.
- [x] 11. Add the CI workflow running `./gradlew build` with the Docker service available for Testcontainers.

## Notes (spike outcome)

- Three source sets per module (`domain`, `application`, `adapter`) with different classpaths work: Spring in `domain` or `application`, and `domain` importing `application`, each fail to compile. The classpath guard is therefore the primary purity mechanism and the grep gate was not needed.
- Spring Modulith discovers modules across Gradle modules that share the root package. It treats a sub-package as internal, so a module exposes its `api` package with a `package-info.java` placed in the `adapter` source set (same package, `@NamedInterface("api")`), which keeps the annotation out of the plain-Java layers. Forgetting it makes `modules.verify()` fail.
- Consumers must resolve the module jar, so the convention clears the empty `main` outgoing variants.
- `app-boot` wiring will live in `io.corebilling.boot`, which both ArchUnit rules and Modulith must treat as non-context.

## Verification

- `cd billing && ./gradlew build` is green.
- Moving the fixture import of `org.springframework` into a real `domain` package makes `./gradlew build` fail (checked manually once, then guarded by the meta-test).
- `./gradlew :app-boot:test --tests '*ArchitectureMetaTest'` passes and fails if any rule is removed from the rule set.
- `grep -rE "org\.springframework|jakarta\.persistence|com\.fasterxml\.jackson" billing/*/src/*/java/**/domain billing/*/src/*/java/**/application` returns nothing (adjust the glob to the source-set layout chosen).
- CI runs the same build on a pull request.

## Risks and rollback

- The source-set convention may fight the IDE or Modulith. Rollback is the grep-gate fallback in step 5; record the weaker guarantee in the phase notes.
- Everything here is new files, so rollback is deleting `billing/`.
