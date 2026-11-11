---
title: "Core Billing phase 10: billing session and customer portal"
date: 2026-11-11
summary: "A scoped, single-use Billing Session exchanged for an HttpOnly cookie, a customer-scoped portal API with CSRF and strict CORS, and Stripe-hosted payment methods; full build green."
---

# Core Billing phase 10: billing session and customer portal

# Core Billing phase 10: billing session and customer portal

## What happened

Phase 10 is built and verified (full `./gradlew build` green). It adds the `session` module and the Customer Portal contract: a Tenant System issues a time-limited, permission-scoped Billing Session for one mapped Customer; the portal exchanges a single-use token for an HttpOnly cookie; every portal query takes the customer from the session principal; and the portal exposes customer, wallet, ledger, usage, charge, invoice, subscription, payment, refund, top-up and payment-method endpoints. Payment methods open Stripe's hosted Billing Portal for a provider customer created once per customer and Stripe configuration.

## Problems worth remembering

- The phase file named `V10__billing_sessions.sql`, but phases 6-9 already used V10-V20. The migration is `V21__billing_sessions.sql`.
- The phase suggested putting the session in `customer`. `customer` must not depend on `payment` (an architecture test enforces it), and the portal reads money, payment and invoicing. The session is its own `contexts:session` module; the portal read composition sits at the HTTP edge in `app-boot`.
- The portal HTTP test first reused a fake Stripe key whose last eight characters matched another test's key. The fake maps those characters to an account id, and the unique Stripe account link rejected the second tenant. Test keys need a random suffix.
- The database constraint `expires_at > created_at` means a test that expires a session must move both timestamps, not only the expiry.
- A session's own metadata needed a permission every session has. `session:view` is always added, so a narrowed session can still read its expiry and permission set without gaining any data access.

## Decisions

- The exchange token and the cookie token are separate. Only SHA-256 digests are stored, and the exchange token is single-use; the cookie token is what later requests match.
- Cookies are host-only, scoped to `/portal`, HttpOnly for the session and readable for the CSRF double-submit token, with `Secure` by default (`BILLING_PORTAL_SECURE_COOKIE=false` for local http). SameSite is Lax and the lifetime never exceeds the session expiry.
- CORS allows only the exact origin the request arrived on, which is the tenant hostname. State-changing portal calls require `X-Portal-CSRF` to match the readable cookie.
- The Tenant's return-URL allowlist is managed through `PUT /api/v1/portal/return-urls` (`portal-settings:manage`), because session creation must validate a stored allowlist.
- Payment methods use Stripe's Billing Portal; `stripe_customers` stores one provider customer per Billing Customer and configuration.
- `billing.portal.enabled=false` rolls the session and portal route group out of service.

## Next steps

- Phase 13 must serve the portal SPA and prove cookie behavior across real tenant subdomains.
- Phase 14 writes the session-module decision into the architecture record and keeps the rollback switch documented.
- Consider an idempotency key for session creation and a retention job for expired sessions.
- Consider reusing a provider customer when the Stripe configuration is rotated on the same account.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
