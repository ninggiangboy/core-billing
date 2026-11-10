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

### How plans work

- A plan has one model and one currency, and its terms live in numbered, immutable **versions**: the rule, the billing cycle, the minimum and maximum charge, the rounding policy and the period it is valid. Publishing a change adds the next version; nothing is edited, and a charge records the plan and version it used, so it can always be explained from the data in force at the time. A retired plan takes no new versions and is no longer offered (estimates and reservations are refused), but usage recorded under it can still be charged.
- Unit prices are exact decimals in **minor units** of the plan's currency (a price of `0.0000004` is allowed). All arithmetic is decimal; the exact total is rounded to whole minor units **once**, by the version's rounding policy (`HALF_UP`, `HALF_EVEN`, `UP`, `DOWN`), and then the minimum and maximum charge apply.
- The six models are strategies behind one interface inside the pricing context; adding a seventh touches nothing outside it, because money only ever receives the final amount.

| Model | Price |
|---|---|
| Time-based | duration x rate, with an optional billing increment (per started minute) |
| Usage-based | quantity through tiers, graduated (each band at its own rate) or volume (the whole quantity at its tier's rate); a first tier priced at zero is an included allowance, which is how a usage threshold is expressed |
| Workload-based | a start fee for every execution plus a rate for the work consumed |
| Event-based | count of events x rate (whole numbers only) |
| Subscription | a fixed amount per billing cycle |
| Hybrid | a fixed amount per cycle plus usage components |

- The first plan that prices a metric fixes the unit it is measured in; a later plan that measures the same metric in another unit is refused.

## Usage Metering

Receives and records Usage from the Tenant System.

- Records Usage Events.
- Supports many Metric types and units of measure.
- Supports recording Usage over time.
- Supports Idempotency so a retry never records the same Usage twice.
- Supports aggregating Usage for billing.
- Keeps a trace of where each Usage originated.

- A usage event is append-only and carries an idempotency key and a source trace back to the tenant system's own event. The key makes a retry of one event, or of a whole all-or-nothing batch, record nothing twice; the same key with different content is a conflict. Aggregation sums quantity and counts events per customer, metric and half-open period. Field shapes and batch limits are in `contracts/openapi/billing.yaml`.

## Cost Estimation

An API that estimates cost before the Tenant System executes a Resource or Workload. It is a pure calculation from a plan version and expected quantities, writes nothing, and uses the same rules and the same single rounding as the real charge, so an estimate and the charge for identical input are the same amount. The estimate can serve as the basis for a Reservation: reserving from an estimate holds exactly the estimated amount, see [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md).

## Charging usage

Pricing reads a customer's usage through the usage context's read-only interface, prices the **not yet rated** events in a period under the plan version in force at the end of the period (or a version the caller names), and charges the wallet through money.

- Each usage event is rated at most once: a link row per event, unique on the event, records that it is accounted for. Repeating the request finds nothing left to rate and returns the earlier rating; usage that arrives late is rated on its own.
- The money charge is keyed by a digest of the event ids, so a retry can never charge twice. A charge the wallet cannot pay is recorded as failed and links nothing, so after a top-up the same request rates the same events again under a new attempt.
- The rating, the links, the charge, the ledger entry and the events commit in one transaction. The rating keeps the plan version and the breakdown of lines, so the amount stays explainable after the plan changes.
- One request rates a bounded number of events (`ChargeFromUsageUseCase.MAX_EVENTS`); a longer period is split by the caller.

## Related

Charging from Usage: [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md). Subscription pricing: [invoicing-and-subscriptions.md](invoicing-and-subscriptions.md).
