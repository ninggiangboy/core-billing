---
title: Tenant-system ownership scoping and contract
date: 2026-11-10
---

# Tenant-system ownership scoping and contract

## Outcome

- A tenant-system credential now reaches only the customers it mapped, and the
  resources owned by those customers: wallets, reservations, charges, payments,
  refunds, usage and ratings. Cross-system access returns 404 exactly like a
  missing resource.
- Webhook endpoints carry the tenant system that registered them; listing,
  reading, changing, rotating and resending are scoped to that owner. Existing
  endpoints migrate as tenant-owned (null owner) and stay visible only to a
  tenant user.
- The full OpenAPI document now records `x-allowed-callers` per operation, and
  `contracts/openapi/billing-tenant-system.yaml` is generated from it with only
  the 39 operations a tenant-system credential may call, `apiKey` security and
  the ownership rule.

## Verification

- `./gradlew build` green, including ArchUnit, Spring Modulith, RLS coverage and
  the OpenAPI drift test.
- New `TenantSystemOwnershipHttpIntegrationTest` covers two systems in one
  tenant across customer, wallet, reservation, charge, usage, pricing, payment
  and webhook reads and writes.
- Generated tenant contract: 39 operations, all with `security: [{apiKey: []}]`,
  no platform-only operation.

## Notes

- Ownership resolves through `customer_mappings`; no duplicate owner column was
  added to money, usage or payment tables.
- A customer mapped to two systems is visible to both; unmapped customers are
  visible only to a tenant user.
- Committed in two phases: 704a978 (ownership) and c295420 (contract).

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
