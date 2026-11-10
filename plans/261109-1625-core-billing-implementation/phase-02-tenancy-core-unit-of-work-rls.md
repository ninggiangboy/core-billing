---
phase: 2
title: "Tenancy core, UnitOfWork and RLS"
status: pending
priority: P1
effort: "4d"
dependencies: [1]
---

# Phase 2: Tenancy core, UnitOfWork and RLS

## Goal

Deliver the `platform` module's tenant model, hostname-based Tenant Context, the transaction boundary that sets the tenant for RLS, and the shared tables every later phase relies on (outbox, idempotency, audit).

## Context

Source: [multi-tenancy-and-data-isolation.md](../../docs/multi-tenancy-and-data-isolation.md), [security-and-reliability.md](../../docs/security-and-reliability.md), DDD report items 3, 4, 6 and 7. Platform administration needs to read tenants across the Platform, but financial tables must stay strictly tenant-scoped.

## Files to Create / Modify

- Create: `billing/platform/` (layered module: `Tenant`, `TenantDomain`, `TenantCode`, tenant use cases, persistence adapter)
- Create: `billing/app-boot/src/main/java/.../infrastructure/` (`SpringUnitOfWork`, `JdbcOutboxAppender`, `JdbcIdempotencyStore`, `JdbcAuditLog`, request-scoped Tenant Context filter)
- Create: Flyway migrations `V1__tenancy.sql` (tenants, tenant_domains), `V2__platform_tables.sql` (outbox, idempotency_keys, audit_log), roles and RLS template
- Create: `billing/app-boot/src/test/java/.../rls/` and `.../unitofwork/` integration tests
- Modify: `billing/shared-kernel` only if a port is missing (stay within the ten-type limit)

## Tasks & Steps

- [ ] 1. Define database roles: a migration owner that owns every table, and an application role that does not own tables and has no BYPASSRLS. Apply `ENABLE` and `FORCE ROW LEVEL SECURITY` on every tenant-scoped table by a migration helper so later phases cannot forget it.
- [ ] 2. Model `Tenant` (unique `tenant-code`, status) and `TenantDomain` (hostname to tenant, kind: platform or tenant subdomain) as the domain-mapping seam. Unique constraints include `tenant_id` where the identifier is tenant-scoped.
- [ ] 3. Implement the Tenant Context as a value object in `shared-kernel` terms (`TenantId` only), resolved once per request from the hostname: `platform.<base-domain>` yields the Platform context, a known subdomain yields a Tenant context, anything else is rejected with a generic error. A client-supplied `tenant_id` is ignored for access decisions.
- [ ] 4. Implement `SpringUnitOfWork`: a `TransactionTemplate` with propagation `REQUIRED`; the first statement is `select set_config('app.tenant_id', ?, true)`. A platform-scoped variant sets no tenant and may touch only platform-owned tables.
- [ ] 5. RLS policy template: `tenant_id = current_setting('app.tenant_id', true)::uuid`. No blanket cross-tenant policy exists on any financial table; Platform administrators read tenant data only by acting in that tenant's explicit, audited context.
- [ ] 6. `outbox` table (id, tenant_id, aggregate, type, payload, created_at, status, attempts, next_attempt_at), `JdbcOutboxAppender` implementing the `EventPublisher` port inside the caller's transaction. The relay arrives in phase 6.
- [ ] 7. `idempotency_keys` table with `unique (tenant_id, scope, key)`, a request hash and the stored response. `JdbcIdempotencyStore` inserts first; a duplicate with the same hash returns the stored result, a duplicate with a different hash is a conflict.
- [ ] 8. `audit_log` table (actor, tenant, action, target, timestamp, request id) with an `AuditLog` port used by administrative use cases.
- [ ] 9. Use cases and persistence for create and deactivate tenant and add domain. REST controllers for them are deliberately left to phase 3, where authentication exists.
- [ ] 10. Row types live in `adapter..persistence`; add a reload-and-compare test for each repository write.

## Verification

- `./gradlew :app-boot:test` includes: a query as the application role with no tenant set returns zero rows; a query with tenant A cannot see tenant B rows; an insert with a mismatched composite foreign key is rejected by the database.
- Rollback test: inject a failure after the outbox append inside `SpringUnitOfWork` and assert zero changed rows in every table.
- Idempotency test: the same key twice yields one stored row and identical results; a changed payload under the same key is rejected.
- ArchUnit rule from phase 1 still passes with real modules present.

## Risks and rollback

- If a connection pool reuses a session, a leaked `app.tenant_id` would cross tenants. `set_config(..., true)` is transaction-local; the isolation test runs two tenants alternately on a one-connection pool to prove no leak.
- Migrations are forward-only; rollback in development is dropping the database. Back up before any change to a shared environment.
