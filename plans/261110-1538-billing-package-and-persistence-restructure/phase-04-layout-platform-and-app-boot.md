---
phase: 4
title: "Layout: platform and app-boot imports"
status: completed
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

- [x] 1. Write the file-to-package mapping for `platform` in the phase notes, including the split of `platform.api`.
- [x] 2. Move `platform` in one commit, compile every module, fix imports.
- [x] 3. Run `./gradlew build`; fix residual references (string class names in Spring config, `@ComponentScan`, test resources that mention a moved package).
- [x] 4. Add `platform` to the layout set; run the architecture tests.
- [x] 5. Verify no Java file sits directly in a layer root package in any module (the rules now cover all 8).

## Phase notes

### Mapping

- `platform.domain.tenant`: Tenant, TenantCode, TenantStatus, TenantDomain, DomainKind, Hostname.
- `platform.domain.role`: Role, RoleId, AuthorizationPolicy.
- `platform.domain.user`: BillingUser, UserId, UserStatus.
- `platform.domain.credential`: ApiCredential, ApiKey, CredentialId, CredentialStatus.
- `platform.domain.tenantsystem`: TenantSystem, TenantSystemStatus.
- `platform.application.tenant.command`: AddTenantDomainUseCase, CreateTenantUseCase, DeactivateTenantUseCase, EnsurePlatformDomainUseCase, ProvisionTenantUseCase, ResolveHostUseCase, HostnameAlreadyMappedException, TenantCodeAlreadyUsedException, TenantMismatchException, TenantNotFoundException; `.tenant.query`: TenantQueries; `.tenant.port`: TenantRepository, TenantDomainRepository.
- `platform.application.role.command`: RoleAdministration, SeedDefaultRolesUseCase, SeedPermissionsUseCase; `.role.port`: RoleRepository, PermissionCatalogRepository.
- `platform.application.user.command`: UserAdministration, BootstrapPlatformAdminUseCase; `.user.port`: UserRepository.
- `platform.application.credential.command`: CredentialAdministration; `.credential.port`: CredentialRepository.
- `platform.application.tenantsystem.command`: TenantSystemAdministration; `.tenantsystem.port`: TenantSystemRepository.
- `platform.application.auth.query`: Authentication, Caller, RateLimitExceededException; `.auth.port`: IdentityProviderPort, RateLimiter. These are the request-pipeline lookups phase 7 keeps as they are.
- `platform.api.actor`: Actor, ActorType, Authenticated, Authorizer, CurrentActor, Permission, PermissionScope, PublicEndpoint, RequestScope, RequiresPermission.
- `platform.api.error`: AccessDeniedException, ConflictException, IdempotencyConflictException, NotFoundException.
- `platform.api.audit`: AuditEntry, AuditLog.
- `platform.api.idempotency`: IdempotencyStore, StoredResponse.
- `platform.api.tenant`: TenantContext, TenantSystemId.
- Each `platform.api` sub-package gets `@NamedInterface("platform-<name>")` in the adapter source set; the root `platform.api` package-info stays.

### Persistence placement

- `platform.adapter.persistence` stays one role package in this phase. Its rows are package-private and the package-private `Causes` helper is shared by five repositories (tenant, role, user and tenant-system); splitting the package now would force either widening `Causes` or duplicating it. The read repositories introduced in phases 5 and 7 use `adapter.persistence.<feature>` packages, which is where phase 5 puts them.
- This matches the `customer` reference (`CustomerRow` and `JdbcCustomerRepository` in one `adapter.persistence` package).

### Result

- `./gradlew build` green; 551 tests, 0 failures (same as phase 1).
- `ArchitectureTest` and `ModulithVerificationTest` pass with all 8 modules in the layout set and the split `platform.api` named interfaces.
- `git grep -nE '^package io\.corebilling\.[a-z]+\.(api|domain|application|adapter);'` finds only `package-info.java` files.

## Verification

- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `git grep -nE 'io\.corebilling\.[a-z]+\.(api|domain|application|adapter);'` shows no Java file declaring a layer root package, other than `package-info`.
- `ModulithVerificationTest` passes.

## Risks and rollback

- Many modules import `platform.api`, so a wrong grouping creates churn for every module. Keep the number of `platform.api` sub-packages small and name them for what they are, not by role.
- Rollback is reverting the single move commit.
