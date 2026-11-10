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

- [ ] 1. Walk `platform` and `customer` and write the list of operations in the phase notes: query side, write side, and request-pipeline lookups that stay as they are.
- [ ] 2. Add the read side for `customer`, one commit per feature, then run `./gradlew :customer:test` and the customer integration and HTTP tests.
- [ ] 3. Add the read side for `platform`, one commit per feature, then run `./gradlew :platform:test` and the access control, API key, identity and RLS tests.
- [ ] 4. Remove the unused list and search methods from the write repositories.
- [ ] 5. Add `platform` and `customer` to the read and write set of the architecture rules.

## Verification

- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `AccessControlHttpIntegrationTest`, `ApiKeyHttpIntegrationTest`, `CustomerHttpIntegrationTest`, `RowLevelSecurityIntegrationTest` and `RlsCoverageIntegrationTest` pass unchanged.
- No class under `..query..` depends on a write repository; no write repository declares a list, page or search method.
- GET responses are identical to those before the change (the existing HTTP tests assert them).

## Risks and rollback

- Moving a request-pipeline lookup to the query side would change the hot authentication path; the phase notes list what stays, and review checks that list.
- Row-level security needs the tenant setting: read repositories must run inside the same `UnitOfWork` transaction as before, otherwise they return no rows.
- Rollback is reverting the feature commit.
