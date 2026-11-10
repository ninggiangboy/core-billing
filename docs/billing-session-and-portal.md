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
