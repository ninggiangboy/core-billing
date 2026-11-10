---
phase: 4
title: "Layout: platform and app-boot imports"
status: pending
priority: P1
effort: "1.5d"
dependencies: [3]
---

# Phase 4: Layout: platform and app-boot imports

## Goal

Move `platform`, the largest module (94 files), to the layout, and bring every import in `app-boot` and its tests up to date, so all 8 modules use the layout.

## Context

`platform.application` has 30 files in one package and `platform.api` has 20. Suggested features: `tenant` (tenant, domain mapping, host resolution), `role` (roles, permission catalog, authorization policy), `user`, `credential` (API credentials and keys), `tenantsystem`, and `auth` (`Authentication`, `Caller`, `RateLimiter`, `IdentityProviderPort`, `Actor`, `Permission`, `AccessDeniedException`). Cross-cutting types every context uses from `platform.api` (`Actor`, `Permission`, `AuditLog`, `IdempotencyStore`, `NotFoundException`, `ConflictException`, `CurrentActor`) are the most imported; split `platform.api` so these land in few, clearly named sub-packages and record each as a named interface.

`platform` and `customer` already use `@Table` rows (`TenantRow`, `UserRow`, ...). Rows are package-private, so each row moves together with its repository into the same feature package under `adapter/persistence`.

## Files to Create / Modify

- Move with `git mv` every file under `billing/platform` in all source sets.
- Modify imports in all other modules and in `billing/app-boot` (`config`, `infrastructure`, `jobs`, `security`, `web`, and all tests under `src/test`).

## Tasks & Steps

- [ ] 1. Write the file-to-package mapping for `platform` in the phase notes, including the split of `platform.api`.
- [ ] 2. Move `platform` in one commit, compile every module, fix imports.
- [ ] 3. Run `./gradlew build`; fix residual references (string class names in Spring config, `@ComponentScan`, test resources that mention a moved package).
- [ ] 4. Add `platform` to the layout set; run the architecture tests.
- [ ] 5. Verify no Java file sits directly in a layer root package in any module (the rules now cover all 8).

## Verification

- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `git grep -nE 'io\.corebilling\.[a-z]+\.(api|domain|application|adapter);'` shows no Java file declaring a layer root package, other than `package-info`.
- `ModulithVerificationTest` passes.

## Risks and rollback

- Many modules import `platform.api`, so a wrong grouping creates churn for every module. Keep the number of `platform.api` sub-packages small and name them for what they are, not by role.
- Rollback is reverting the single move commit.
