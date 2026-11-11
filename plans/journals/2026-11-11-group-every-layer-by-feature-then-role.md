---
title: Group every layer by feature then role
date: 2026-11-11
summary: "domain, application and adapter now use feature then role in every context; api is named by capability; five multi-feature controllers split; two ArchUnit rules added; OpenAPI changed only in tags"
---

# Group every layer by feature then role

## What happened

The previous package pass left three inconsistencies: the application layer went feature then role while the adapter layer went technology then feature, the single-feature contexts (customer, usage) were flat while the others were not, and the classes that serve other contexts through an api interface had five different names in two packages.

This pass applied one rule everywhere:

- `domain`, `application` and `adapter` are grouped feature first, then role, in every context. customer and usage gained a feature folder (`customer.application.customer.command`), which repeats the context name the way `customer.domain.customer` already did.
- `api` is the one exception. Its packages are named for the capability a caller uses (`lookup`, `invoice`, `wallet`), so `customer.api.customer` was tried and reverted, and `usage.api.usage` became `usage.api.lookup`.
- The adapter is `adapter.<feature>.<technology>`: `persistence`, `web`, `messaging`, and the packages of an external system (`provider`, `keycloak`, `http`, `crypto`, `json`). Seventy-two classes moved by script and five controllers were split by hand: Money into wallet, ledger, reservation and charge; Pricing into plan and rating; Invoicing into invoice and subscription; Webhook into endpoint and delivery; Payment into payment, refund, stripe and incident.
- The `shared` folders of payment and webhook were dissolved into the feature that owns each class.
- The class that implements an api interface for another context is `<Interface>Service` in `service`: `CustomerLookupService`, `WalletLookupService`, `ChargeLookupService`, `MetricCatalogService`, `PlanPricingService`, and a new `UsageLookupService`. `UsageQueries` used to implement `UsageLookup` itself, so the lookup was extracted into a thin service that delegates to it.

## Verification

- 702 tests, 0 failures, including 45 integration classes on Docker.
- A script compared every moved file with HEAD: 103 files differ only in `package` and `import` lines, and the one exception is `UsageQueries`.
- The OpenAPI contract changed in tag lines only, 86 lines in both files. No path, method, operationId or schema changed, which also shows the split controllers expose the same endpoints.
- ArchUnit: 31 tests on production code and 28 meta tests against the fixtures, including the two new rules, `api-implementations-are-services` and `adapter-classes-in-feature-then-technology`. The Stripe SDK rule was narrowed to `payment.adapter.stripe.provider..` instead of being loosened to the whole Stripe adapter.

## What broke on the way

- The generator for the customer and usage moves was given the wrong sub-path and produced no moves. The compile still passed, which would have hidden the mistake; the file listing caught it.
- Two `AccessControlHttpIntegrationTest` tests filtered controllers by the old `adapter.web` package name and failed once the controllers moved. They failed loudly, but a filter that matches nothing could just as easily have let a test pass with no subjects, so any filter by package name deserves a non-empty check.
- `RefundController.RefundResponse.ofView` had to become public because the payment controller now reads it from another package.

## Decision

The tag names in the OpenAPI contract changed (`money-controller` became `wallet-controller` and so on). This was accepted because the system is not in production and `web/` and `sample-tenant/` have no generated client yet. Rename tags deliberately if a client generator is added later.

Still open and left alone: the duplicate `SecretCipher` ports, domain-event naming, id naming, the `sharedkernel` split and feature-package cycles.

## Next steps

- Commit this pass.
- Decide the five open items above.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
