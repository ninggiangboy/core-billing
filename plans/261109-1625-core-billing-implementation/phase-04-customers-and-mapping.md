---
phase: 4
title: "Customers and tenant mapping"
status: completed
priority: P1
effort: "2d"
dependencies: [3]
---

# Phase 4: Customers and tenant mapping

## Goal

Let a Tenant System create and look up Billing Customers and map its own users to them, with the mapping unambiguous across tenants and tenant systems.

## Context

Source: [customers-and-tenant-mapping.md](../../docs/customers-and-tenant-mapping.md). A Customer belongs to one Tenant and does not need a login. One Tenant User maps to exactly one Customer. The `customer` module is the first module built on the full pattern from phases 1 to 3.

## Files to Create / Modify

- Create: `billing/customer/` (`Customer`, `TenantCustomerMapping`, use cases, ports, persistence adapter, REST adapter, public `api` package exposing customer lookup)
- Create: Flyway `V4__customers.sql`
- Modify: `contracts/openapi/billing.yaml` (regenerated)

## Tasks & Steps

- [x] 1. Schema: `customers(tenant_id, id, ...)` and `customer_mappings(tenant_id, tenant_system_id, external_user_id, customer_id)`. Unique `(tenant_id, tenant_system_id, external_user_id)`. Composite foreign keys to `customers(tenant_id, id)` and `tenant_systems(tenant_id, id)`. RLS enabled through the phase 2 helper.
- [x] 2. Domain: `Customer` aggregate with status; mapping rule that one external user maps to one customer within one tenant system.
- [x] 3. Use cases: create customer with mapping (idempotent by `Idempotency-Key`), get customer by external user id, list and update customers for administrators, deactivate.
- [x] 4. REST for Tenant Systems (API key principal) and for Tenant Users (OIDC principal, Permission-guarded).
- [x] 5. Publish `customer.created` through the outbox appender; no consumer yet.
- [x] 6. `customer.api` exposes the lookups that `money` and `payment` will need; nothing else is importable from other modules.

## Notes

- The migration is `V5__customers.sql`. Mappings are part of the `Customer` aggregate (a child collection), so a customer update never loses them; the unique mapping key and both composite foreign keys are enforced by the database.
- Idempotency stores the new customer's id as the response body, so a retry reloads the same customer instead of re-serialising a response.
- `platform.api` now also holds `NotFoundException` and `ConflictException`, and `docs/architecture.md` records it as the shared contract every context may use.
- A database integrity violation maps to a generic 409; the detail goes to the log only.

## Verification

- Same `external_user_id` in two tenants produces two independent customers (scenario 15 foundation).
- Retrying the create call with the same key returns the same customer and one mapping row.
- Cross-tenant composite foreign key violation is rejected by the database in a test.
- Architecture rules and the reload-and-compare repository test pass.

## Risks and rollback

- Low risk. Rollback is reverting the module and migration before any dependent phase starts.
