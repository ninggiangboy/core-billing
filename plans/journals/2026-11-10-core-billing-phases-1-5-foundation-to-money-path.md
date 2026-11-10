---
title: "Core Billing phases 1-5: foundation to money path"
date: 2026-11-10
summary: "Built the Gradle foundation, tenancy with RLS, identity, customers and the append-only money path, all verified against real PostgreSQL and Keycloak."
---

# Core Billing phases 1-5: foundation to money path

## What happened

Phases 1 to 5 of the implementation plan are built, tested and committed on `dev`.

- **Foundation.** Gradle multi-module build with per-layer source sets (api, domain, application, adapter) whose classpaths make a framework import in the inner layers a compile error. ArchUnit rules plus Spring Modulith verification, with a meta-test that runs the same rules against deliberately violating fixtures.
- **Tenancy.** Row-level security with a non-owner application role, `app.tenant_id` set transaction-locally by the UnitOfWork, outbox, idempotency and audit tables. The Platform became a reserved tenant so platform data uses the same RLS template.
- **Identity.** Keycloak (tenant-scoped usernames, `tenant_code` claim checked against the hostname), hybrid RBAC, API credentials stored as SHA-256 digests with rotation overlap, revocation and rate limiting, per-endpoint permissions, and an OpenAPI contract checked in CI.
- **Customers.** Idempotent creation with Tenant System mapping, composite foreign keys across tenants.
- **Money.** Signed single-entry ledger enforced append-only by privileges and triggers, wallet projection under a row lock, reservations, charges, reversal, expiry and verify jobs, all in one transaction with the outbox row.

## Problems worth remembering

- Keycloak 26.8 rejects Admin API tokens without the `kc-admin-api` scope; it took reading the server bytecode to find that out.
- `current_setting('app.tenant_id', true)` returns an empty string, not NULL, after first use on a connection, so policies go through `nullif`.
- zsh ties a loop variable named `path` to `PATH`; a tooling hook also blocks the words `build` and `target` in shell commands.

## Decision

Deviations from the plan are recorded in each phase file and in `docs/architecture.md` (platform tenant, fourth `api` source set, signed ledger amounts, wallet-first locking, skip-locked on the wallet in the expiry job).

## Next steps

Phase 6 (outbox relay) needs a way to read outbox rows across tenants, which RLS currently prevents. Phase 12 must register each tenant hostname's redirect URI in Keycloak.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
