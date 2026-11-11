---
phase: 10
title: "Billing session and portal API"
status: completed
priority: P1
effort: "3d"
dependencies: [5, 8]
---

# Phase 10: Billing session and portal API

## Goal

Let a Tenant System create a time-limited, scoped Billing Session for one Customer and give the Customer Portal a read and pay API that can never reach another Customer or Tenant.

## Context

Source: [billing-session-and-portal.md](../../docs/billing-session-and-portal.md). The Customer needs no Billing login. This adds the third principal type to the phase 3 security filter chain.

## Files to Create / Modify

- Create: in `billing/customer/` or a clearly separated package in it: `BillingSession` aggregate, session exchange and portal read use cases, portal REST under `/portal/api/`
- Create: Flyway `V10__billing_sessions.sql`
- Modify: the security filter chain from phase 3 to add the session principal

## Tasks & Steps

- [x] 1. `BillingSession`: id, tenant, customer, permissions (scope), expiry, allowed return URL, status. The opaque token is high-entropy, only its hash is stored, and it is single-use.
- [x] 2. Create API for Tenant Systems: returns a portal URL on the tenant hostname. The return URL must match the Tenant's allowlist.
- [x] 3. Exchange: the portal opens the URL, Billing validates and consumes the token and sets an HttpOnly, Secure, SameSite cookie bound to the tenant host with an expiry not later than the session expiry; a token for tenant A on tenant B's host fails.
- [x] 4. Portal principal carries tenant, customer and permissions only. Every portal query takes the customer from the principal, never from a path or body value.
- [x] 5. Portal endpoints (each guarded by a session permission): customer info, wallet and balance, ledger history, usage and charges, invoices, subscriptions, payments and refunds, create top-up through the Tenant's Stripe configuration, payment method management via Stripe-hosted flows.
- [x] 6. CSRF protection for cookie-authenticated state-changing calls; strict CORS limited to the tenant host.
- [x] 7. Expiry and revocation: expired or revoked sessions fail immediately; Tenant Systems can revoke a session.
- [x] 8. Publish no events here; audit session creation and consumption.

## Verification

- A session for customer A cannot read customer B in the same tenant, nor any customer in another tenant (tests with crafted ids in paths and query strings).
- A reused or expired token fails; a session with a narrowed permission set gets 403 on the excluded endpoints.
- A tampered return URL is rejected; state-changing portal calls without the CSRF token are rejected.
- Top-up through the portal credits the wallet only after the verified Stripe event (reuses the phase 8 test fixture).

## Risks and rollback

- Cookie handling across sample tenant and portal hostnames must be tested with real subdomains in the e2e phase.
- Rollback is disabling the portal route group by configuration; no financial data depends on it.

## Notes

- Migration numbers moved with the later phases; the portal tables are `V21__billing_sessions.sql`, not `V10`.
- The session and portal live in the new `contexts:session` module (the architecture's "billing session" bounded context). The portal reads money, payment and invoicing, and `customer` must not depend on `payment`, so the read composition sits at the HTTP edge in `app-boot` instead of inside `customer`.
- Payment methods use Stripe's hosted Billing Portal. A provider customer is created once per Billing Customer and Stripe configuration in `stripe_customers`.
- The tenant's return-URL allowlist is managed through `PUT /api/v1/portal/return-urls` (`portal-settings:manage`), because creation must validate a stored allowlist.
