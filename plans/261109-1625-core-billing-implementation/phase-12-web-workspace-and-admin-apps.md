---
phase: 12
title: "Web workspace and admin apps"
status: pending
priority: P2
effort: "8d"
dependencies: [3]
---

# Phase 12: Web workspace and admin apps

## Goal

Create the pnpm workspace with shared packages and ship the Platform Administration and Tenant Administration apps against the Billing API.

## Context

Source: [overview-and-scope.md](../../docs/overview-and-scope.md), [authentication-and-authorization.md](../../docs/authentication-and-authorization.md). Stack: React, Vite, TanStack Router and Query. UI kit is Tailwind CSS with shadcn/ui (assumption 7). This phase starts after phase 3 publishes the OpenAPI file and then follows each later API; screens for an API that does not exist yet wait for it.

## Files to Create / Modify

- Create: `web/pnpm-workspace.yaml`, root TypeScript and lint configs, `web/packages/api-client` (generated from `contracts/openapi/billing.yaml`), `web/packages/auth` (OIDC PKCE), `web/packages/ui`, `web/packages/config`
- Create: `web/apps/platform-admin` and `web/apps/tenant-admin`
- Create: Playwright config and tests; CI workflow `web.yml`

## Tasks & Steps

- [ ] 1. Workspace setup with the package manager lockfile, shared TypeScript and lint configs, and scripts for `typecheck`, `lint`, `test`, `build` across all packages.
- [ ] 2. `api-client`: generate typed client and types from the OpenAPI file; a CI step fails when generated output is stale.
- [ ] 3. `auth`: Keycloak OIDC authorization code flow with PKCE; the login username is built as `{tenant-code}:{email}` from the hostname's tenant; token refresh; permission-aware route guards that mirror, but never replace, server checks.
- [ ] 4. `ui`: tokens, layout shell, tables with server pagination, forms with validation, dialogs, toasts, loading and error states, accessibility checks.
- [ ] 5. `platform-admin`: tenants and domains, platform users, roles and permissions, audit log, Connect platform settings view.
- [ ] 6. `tenant-admin`: users and roles, tenant systems and API credentials (secret shown once), webhook endpoints and delivery history with resend, Stripe configuration (write-only secrets), pricing plans and versions, customers and mappings, wallets and ledger with reversal, usage and charges, invoices, subscriptions, payments and refunds.
- [ ] 7. Tenant resolution in the browser comes from the hostname only; no tenant id is typed or stored client-side.
- [ ] 8. Playwright flows: sign in as the same email in two tenants, create a tenant system and see the secret once, configure a webhook, create a plan and see it listed.

## Verification

- `pnpm -C web typecheck lint test build` passes.
- Playwright flows pass against a Billing instance with Keycloak from docker-compose.
- Bundle contains no secrets (grep of build output for key patterns returns nothing); API secrets are not persisted to browser storage.
- Keyboard-only navigation works for the main flows (checked in the Playwright accessibility pass).

## Risks and rollback

- Generated-client churn while APIs still change: pin the generation step to the committed OpenAPI file and regenerate in the same commit as the API change.
- A UI kit change late is expensive: confirm assumption 7 before step 4.
