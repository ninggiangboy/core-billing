# Billing Session and Customer Portal

Billing provides a standalone Billing Portal where a Customer manages financial information. It works like Stripe Checkout and the Stripe Customer Portal and does not depend on the Tenant System's UI or its account system.

## Billing Session

The Tenant System creates a Billing Session through an API.

- Created for one specific Billing Customer.
- Has an expiry time.
- Has a predefined access scope.
- Never grants access to another Customer.
- Supports Redirect to the Billing Portal.
- Can limit functions through Session Permissions.

A Customer acts only within the scope of the Session issued to them. A Session from one Tenant is not usable in another, see [security-and-reliability.md](security-and-reliability.md).

## Customer Portal functions

- View Customer information.
- View Wallet and balance.
- View Ledger and transaction history.
- View Usage and Charges.
- View Invoices.
- Top up and pay.
- View Subscriptions.
- View Payment and Refund history.
- Manage payment methods within what Stripe supports.

## Related

Customers: [customers-and-tenant-mapping.md](customers-and-tenant-mapping.md). Payments: [payments-and-stripe.md](payments-and-stripe.md).

## How Billing does it

**Creating a session.** A Tenant System calls `POST /api/v1/billing-sessions` with a `customerId`, a `returnUrl` and optionally a subset of session permissions and a lifetime. The customer must be mapped to that Tenant System, and `returnUrl` must match one of the Tenant's allowlisted URLs (`PUT /api/v1/portal/return-urls`, guarded by `portal-settings:manage`). The response carries a portal URL on the Tenant's own hostname. The URL holds a 256-bit single-use exchange token in its fragment, so it never reaches a server log or a Referer header; only the SHA-256 digest is stored. A session lasts one hour by default, between one minute and one day.

**Exchange.** The portal reads the token from the fragment and calls `POST /portal/api/session/exchange`. Billing consumes the token and sets two host-only cookies scoped to `/portal`: an HttpOnly `billing_portal` cookie (the session token) and a readable `billing_portal_csrf` cookie. `Secure` is the production default, `SameSite=Lax`, and the cookie lifetime never exceeds the session's expiry. The exchange token cannot be used twice. A token issued for one Tenant is unknown on another Tenant's hostname, because the lookup runs inside the Tenant scope resolved from the host.

**Principal and scoping.** The portal principal carries the Tenant, the one Customer and the session permissions. It has no Billing User and no Tenant System. Every portal query takes the customer from the principal, never from a path or body; a resource that belongs to another customer answers 404 exactly like one that does not exist. Each endpoint is guarded by a session permission: `session:view`, `customer:view`, `wallet:view`, `ledger:view`, `usage:view`, `charge:view`, `invoice:view`, `subscription:view`, `payment:view`, `refund:view`, `top-up:create` and `payment-method:manage`. Every session gets `session:view` and, when its creator names none, every read permission; `top-up:create` and `payment-method:manage` are opt-in.

**Endpoints.** `/portal/api/customer`, `/wallets`, `/wallets/{id}`, `/wallets/{id}/ledger`, `/charges`, `/usage/history`, `/usage/totals`, `/invoices`, `/invoices/{id}`, `/subscriptions`, `/subscriptions/{id}`, `/payments`, `/payments/{id}`, `/refunds`, `POST /top-ups` and `POST /payment-methods`. A top-up goes through the Tenant's Stripe configuration and credits the wallet only when the verified Stripe event arrives. Payment methods open Stripe's hosted Billing Portal for a provider customer created once per Billing Customer and configuration.

**Hardening.** State-changing portal calls need the `X-Portal-CSRF` header matching the readable cookie (double submit). CORS allows only the exact origin the request arrived on, which is the tenant hostname. Expired or revoked sessions fail immediately; a Tenant System may revoke only the sessions it issued, a Tenant User any in the Tenant. Creation, exchange and revocation are audited. The portal publishes no events. Set `billing.portal.enabled=false` (`BILLING_PORTAL_ENABLED`) to roll the session and portal endpoints out of service; no financial data depends on them.
