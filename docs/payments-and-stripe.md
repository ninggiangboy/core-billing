# Payments and Stripe

Stripe processes payment transactions. Billing synchronizes status and updates the financial ledger.

## Payment Provider model

Billing supports multiple Payment Providers through a Provider Integration mechanism. Stripe is the only provider in scope now; others can be added later.

- Each Tenant configures and manages its own Payment Provider.
- Each Tenant has one Stripe Account Configuration.
- Stripe is not a shared payment account for all Tenants. Every Tenant owns an independent configuration.
- Payments and Refunds run through the corresponding Tenant's configuration.
- Transaction data and integration information are isolated between Tenants.

## Integration modes

**Direct.** The Tenant supplies its own Stripe API credentials.
- The Tenant manages its own Stripe Account and provides the Secret Key and any needed authentication information.
- Billing uses those credentials to perform Payments and Refunds.
- Webhooks are verified with the matching Webhook Secret.

**Connect.** The Tenant links a Stripe account through Stripe Connect, without giving Billing a Secret Key.
- The Platform owns the Stripe Platform Account used for Connect.
- The Tenant goes through the Stripe account-linking flow.
- Billing stores the Connected Account information.
- Transactions run on the correct Connected Account under the chosen Connect model.
- Billing tracks the connection status and which Stripe capabilities the Connected Account can use.
- Billing receives and verifies Webhooks from Stripe Connect.

The Platform-level Connect configuration is separate from the Tenant's Stripe Account configuration: the Platform manages the Connect Application and OAuth, and the Tenant owns the Connected Account.

## Connect money-flow model

Stripe Connect has three money-flow models: Direct Charges, Destination Charges, and Separate Charges and Transfers. They differ in who collects fees, who pays Stripe fees, and who carries Refunds and Disputes.

The working decision is **Direct Charges**: the charge is created on the Tenant's connected account, so the Tenant bears fees, refunds and disputes. The specification leaves the final model to the Payment domain design. The decision and its trade-offs are in [architecture.md](architecture.md).

## Stripe Account Configuration

Each Tenant has at most one active configuration at a time.

| Attribute | Description |
|---|---|
| Integration Mode | Direct or Connect |
| Account ID | Stripe Account ID |
| Secret Key | Credential for Direct mode |
| Publishable Key | Public key, if needed |
| Webhook Secret | Secret that verifies Webhooks |
| Environment | Test or Live |
| Connection Status | State of the connection |
| Connected At | When the account was linked |
| Last Verified At | Most recent verification time |

Sensitive values are encrypted or kept in a Secret Manager. The Secret Key and Webhook Secret are never returned by ordinary configuration read APIs.

## Payment and Refund

- Create Payments through the Tenant's Stripe Configuration.
- Support topping up the Wallet.
- Synchronize Payment status.
- Support full and partial Refunds where the business rules allow.
- Guarantee Idempotency for Payments and Refunds.
- Reconcile transactions with Stripe.
- Update Wallet and Ledger from verified transaction results only.
- Never record success from the browser Redirect result alone.
- Refunds also update the related Wallet and Ledger transactions.

## Stripe webhooks

- Supported for both Direct Integration and Stripe Connect.
- The signature is verified.
- The related Tenant and Stripe Account are identified.
- Duplicate Events are not processed twice.
- Retry and failed-event handling are supported.
- Processing history is recorded.
- Late and out-of-order Webhooks are handled, see [security-and-reliability.md](security-and-reliability.md).

## How Billing does it

**Taking a payment.** A Tenant System (or administrator) asks for a top-up of a wallet with an `Idempotency-Key`. Billing stores the payment as `REQUIRES_ACTION`, creates a Stripe-hosted Checkout Session on the Tenant's account (a Direct charge, so the Tenant is the merchant), and returns the hosted page's URL. Which payment methods appear is decided by the Tenant's Stripe Dashboard settings. The wallet is not touched. When Stripe's verified webhook says the money arrived, Billing credits the wallet, marks the payment `SUCCEEDED` and writes `payment.succeeded` to the outbox in one transaction. There is one credit per payment, however many events or reconciliation runs report it. A replay with the same key returns the stored payment and answers 200; the same key with a different request is a 409.

**Payment states** only move forward: `REQUIRES_ACTION`, then `PROCESSING` (a delayed method such as a bank debit), then one of `SUCCEEDED`, `FAILED` or `CANCELED`, which never change again. A declined card is recorded as an attempt and leaves the payment open, because the customer may try another card in the same session; the payment fails when the session expires after declines, or when a delayed method fails. A late or repeated event is harmless. An event that contradicts a final state (Stripe says paid for a payment Billing already failed) is not applied and raises an incident for a person.

**Webhooks.** Stripe calls `POST /stripe/webhooks/direct/{configurationId}` (one per Direct configuration, signed with that configuration's secret) or `POST /stripe/webhooks/connect` (the Platform's, one for all connected accounts). These endpoints take no login: the signature of the raw body is checked first, then the tenant is found from Billing's own routing table, never from the request. Verified events are stored once per account and event id, then processed; a replay is answered "duplicate". An event that cannot be applied yet (for example a payment not stored yet) is retried with backoff and gives up after a bounded number of attempts; every attempt is kept as history, and an administrator can see failed events and retry one. Events Billing did not start (the Tenant may use the account for other things) are ignored. A disconnected configuration keeps answering, so payments and refunds it started still settle.

**Invoice payments.** A payment can also settle one invoice, through the same Checkout and the same verified-event path. It succeeds without a ledger entry of its own: the payment context announces `payment.succeeded` with the `invoiceId`, and invoicing credits the wallet and marks the invoice paid from that event (see [invoicing-and-subscriptions.md](invoicing-and-subscriptions.md)). A payment of an invoice is not refundable through Billing. A replay with the same key returns the stored payment and answers 200.

**Refunds** are full or partial and never add up to more than was captured. Refunding a top-up is limited to the unspent balance: the amount is held in the wallet first (so it cannot be spent while Stripe works), Stripe is asked with an idempotency key, and the wallet is debited only when Stripe reports the refund succeeded. A failed refund releases the hold. A refund made directly in the Stripe Dashboard is not applied automatically; it is flagged. The hold lasts at most seven days (the money context's limit); a refund Stripe settles after that is debited only if the wallet still has the balance, otherwise the event is flagged for a person and the wallet is never driven negative. A replay with the same key returns the same refund and answers 200; using the key for a refund of another payment is a 409.

**Reconciliation** runs hourly per Tenant and on demand. It lists recent Checkout Sessions and refunds, applies what Stripe has settled that Billing missed through the same path a verified event takes, and flags anything where Billing is ahead of Stripe without changing it.

**Connect.** The Platform offers Connect only when its Stripe platform key and Connect webhook secret are set. A Tenant starts linking, Billing creates a connected account (SaaS model: the Tenant is the merchant, pays Stripe's fees and carries losses) and returns Stripe's hosted onboarding link; the Billing UI calls refresh on the return path and asks for a new link on the refresh path. Capability status is read from Stripe's v2 account (`configuration.merchant.capabilities.card_payments`) and the configuration is `ACTIVE` only while card payments are active.

**Configuration** endpoints never return a secret, only whether a webhook secret is set, and show the webhook URL to enter in Stripe. Replacing a configuration with the same Stripe account (a rotated key) keeps earlier payments and refunds working; another tenant cannot link an account while this one holds it.

Permissions: `stripe:read` and `stripe:manage` (administrators), `payment:read` and `payment:create` (also grantable to a Tenant System), `refund:create` (administrators only, never a Tenant System). Settings are the `billing.payments.*` properties in `application.yml`; `.env.example` lists the environment variables. The REST shapes are in [contracts/openapi/billing.yaml](../contracts/openapi/billing.yaml) and the event shapes in [contracts/events](../contracts/events/README.md).

## Related

Outbound notifications of payment results: [webhooks-and-events.md](webhooks-and-events.md). Card data is never stored by Billing, see [security-and-reliability.md](security-and-reliability.md).
