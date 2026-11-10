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

## Related

Outbound notifications of payment results: [webhooks-and-events.md](webhooks-and-events.md). Card data is never stored by Billing, see [security-and-reliability.md](security-and-reliability.md).
