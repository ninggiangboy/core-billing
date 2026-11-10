---
phase: 7
title: "Write and read models: platform and customer"
status: pending
priority: P1
effort: "3d"
dependencies: [6]
---

# Phase 7: Write and read models: platform and customer

## Goal

Add the read side to `platform` and `customer`, whose write side already uses `@Table` Rows, so all 8 modules follow the same split.

## Context

`TenantQueries`, the admin list and get operations, and `CustomerAdministration` / `CustomerLookupService` read through the same repositories that the use cases write with (`JdbcTenantRepository.list`, `JdbcCustomerRepository.list`, and so on). The new read side follows phase 5: views, `*ReadRepository` ports, `Jdbc*ReadRepository` selecting directly into the views.

Scope decision for `platform`: authentication and authorization lookups used by the security filters and the request pipeline (`Authentication`, `CallerResolver`, host resolution, credential and user lookup by key, rate limiting) are part of how a request is accepted, not a read model, and they run on every request. They stay on the ports they use today and are listed in the phase notes. Only the list and get operations that the admin GET endpoints and other modules' lookups expose move to the query side. `CustomerLookup` in `customer.api` is a cross-module read and moves to the query side.

Write repositories keep add, update, find by id or key, and lock; list, page and search methods move out.

## Files to Create / Modify

- Create: `*View` records, `*ReadRepository` ports and `Jdbc*ReadRepository` classes in `billing/platform` and `billing/customer`.
- Modify: the existing repositories (remove list and search methods), `TenantQueries`, the query classes in `customer`, the GET handlers of the platform and customer controllers, and the wiring in `billing/app-boot/.../config/` (`PlatformConfiguration`, `CustomerConfiguration`).

## Tasks & Steps

- [x] 1. Walk `platform` and `customer` and write the list of operations in the phase notes: query side, write side, and request-pipeline lookups that stay as they are.
- [x] 2. Add the read side for `customer`, one commit per feature, then run `./gradlew :customer:test` and the customer integration and HTTP tests.
- [x] 3. Add the read side for `platform`, one commit per feature, then run `./gradlew :platform:test` and the access control, API key, identity and RLS tests.
- [x] 4. Remove the unused list and search methods from the write repositories.
- [x] 5. Add `platform` and `customer` to the read and write set of the architecture rules.

## Phase notes

### Operations: query side, write side, request pipeline

`customer`:
- Query side: `list`, `get`, `getByExternalUser` (`CustomerQueries`, `CustomerReadRepository`, `JdbcCustomerReadRepository`, `CustomerView`/`CustomerIdentityView`); `CustomerLookup` (`CustomerLookupService`) now reads through the same read port.
- Write side: create, update, deactivate; `CustomerRepository` keeps add/update/findById/findByExternalIdentity and loses `list`.

`platform`:
- Query side: tenant `get/list/domains`; role `get/list`; user `get/list`; credential `list` per tenant system; tenant-system `get/list`. Each has a `*View`, a `*ReadRepository`, a `*Queries` class and a `Jdbc*ReadRepository` in `adapter/persistence/<feature>`.
- Write side: provision/deactivate tenants and domains, seed roles/permissions, create/assign users, create/rotate/revoke credentials, create tenant systems. The write ports lose `list`; `CredentialRepository.findByTenantSystem` stays because rotation reads the live credential to decide, and the credential GET validates the system through `TenantSystemReadRepository`.
- Request pipeline that stays as it is (not a read model): `Authentication`, `Caller`, `RateLimiter`, `IdentityProviderPort`, `ResolveHostUseCase` and the credential/user lookup by key or subject.

### Placement correction found by the new rules

- `Authentication`, `Caller` and `RateLimitExceededException` were under `platform.application.auth.query`; the read/write rule correctly rejected them because they are the request pipeline, not read models. They now live in `platform.application.auth` (feature root), and `TenantNotFoundException` moved from `tenant.command` to `tenant` so `TenantQueries` no longer depends on the command side.
- `TenantView` is also what `PlatformBootstrap` and `TenantSweep` iterate; both construct `TenantId` from the view id.

### Result

- `./gradlew build` green; 551 tests, 0 failures (same as phase 1).
- `AccessControlHttpIntegrationTest`, `ApiKeyHttpIntegrationTest`, `CustomerHttpIntegrationTest`, `RowLevelSecurityIntegrationTest`, `RlsCoverageIntegrationTest`, `TenantResolutionIntegrationTest`, `IdentityRepositoryIntegrationTest` and `UserAdministrationIntegrationTest` pass.
- No write port declares a list/page/search method; no query class depends on a write port; `ArchitectureTest` and `ModulithVerificationTest` pass with all 7 contexts in the read/write set.

## Verification

- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `AccessControlHttpIntegrationTest`, `ApiKeyHttpIntegrationTest`, `CustomerHttpIntegrationTest`, `RowLevelSecurityIntegrationTest` and `RlsCoverageIntegrationTest` pass unchanged.
- No class under `..query..` depends on a write repository; no write repository declares a list, page or search method.
- GET responses are identical to those before the change (the existing HTTP tests assert them).

## Risks and rollback

- Moving a request-pipeline lookup to the query side would change the hot authentication path; the phase notes list what stays, and review checks that list.
- Row-level security needs the tenant setting: read repositories must run inside the same `UnitOfWork` transaction as before, otherwise they return no rows.
- Rollback is reverting the feature commit.
