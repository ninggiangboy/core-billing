# Pricing and Usage

## Pricing Engine

Defines and calculates prices for services under these models:

- Time-based
- Usage-based
- Workload-based
- Event-based
- Subscription
- Hybrid

A Pricing Plan can define:

- Billing unit
- Unit price
- Price tiers (Tiered Pricing)
- Usage thresholds
- Billing cycle
- Minimum and Maximum Charge
- Rounding policy
- Currency
- Validity period

## Usage Metering

Receives and records Usage from the Tenant System.

- Records Usage Events.
- Supports many Metric types and units of measure.
- Supports recording Usage over time.
- Supports Idempotency so a retry never records the same Usage twice.
- Supports aggregating Usage for billing.
- Keeps a trace of where each Usage originated.

## Cost Estimation

An API that estimates cost before the Tenant System executes a Resource or Workload. The estimate can serve as the basis for a Reservation, see [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md).

## Related

Charging from Usage: [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md). Subscription pricing: [invoicing-and-subscriptions.md](invoicing-and-subscriptions.md).
