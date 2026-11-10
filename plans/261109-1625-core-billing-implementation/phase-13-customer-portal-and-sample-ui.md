---
phase: 13
title: "Customer portal and sample tenant UI"
status: pending
priority: P2
effort: "7d"
dependencies: [10, 11, 12]
---

# Phase 13: Customer portal and sample tenant UI

## Goal

Ship the standalone Customer Billing Portal that works from a Billing Session, and the Sample Tenant UI that lets a person run the reference scenarios by hand.

## Context

Source: [billing-session-and-portal.md](../../docs/billing-session-and-portal.md), [sample-tenant-system.md](../../docs/sample-tenant-system.md). The portal depends only on the Billing portal API and cookie session, not on any Tenant System UI. The sample UI talks only to the Sample Tenant backend, never to Billing directly.

## Files to Create / Modify

- Create: `web/apps/customer-portal`, `web/apps/sample-tenant-ui`
- Modify: `web/packages/ui` (shared components added as real reuse appears), `web/packages/api-client` (portal client; the sample UI gets its own client for the sample backend, generated from that backend's OpenAPI)
- Create: Playwright flows for both apps

## Tasks & Steps

- [ ] 1. `customer-portal` entry route: exchanges the session token, handles expiry and invalid-token screens, and redirects to the return URL on exit.
- [ ] 2. Portal screens: customer information, wallet and balance, ledger history, usage and charges, invoices (with pay), subscriptions, payments and refunds, top-up flow using the Stripe-hosted page and a return page that shows pending until the server reports success, payment methods through Stripe-hosted management.
- [ ] 3. The return page after payment polls the Billing payment status and never infers success from the redirect parameters.
- [ ] 4. `sample-tenant-ui`: registration and login, catalog browsing with cost estimation, order creation with reservation outcome, resource list and lifecycle state, usage view, billing links that create a session and open the portal, webhook-driven notices.
- [ ] 5. A tenant switcher for development only (hostname-based) to demonstrate two tenants side by side without implying client-side tenant selection in production.
- [ ] 6. Accessibility, responsive layout and error states for both apps using the shared `ui` package.
- [ ] 7. Playwright flows: order a resource through to a charge, top up through Stripe test mode, view invoice, expired-session handling.

## Verification

- `pnpm -C web typecheck lint test build` passes for all four apps.
- Playwright flows pass against docker-compose (Billing, sample tenant, Keycloak, PostgreSQL, Stripe in test mode or a recorded fixture server for CI).
- Manually or by test: success page for a failed payment shows failure and the wallet is unchanged.

## Risks and rollback

- Stripe-hosted flows are hard to automate; keep the Stripe test-mode path as a separately tagged test and use a webhook-event injector for the CI path.
