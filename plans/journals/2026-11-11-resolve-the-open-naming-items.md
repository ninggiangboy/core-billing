---
title: Resolve the open naming items
date: 2026-11-11
summary: "One SecretCipher port, ids named after aggregates, events held in *Events classes, acyclic domain features, grouped shared kernel and a web layer free of persistence"
---

# Resolve the open naming items

## What happened

The feature-then-role regroup left six items open. This pass settled each one and added a build rule where the convention can be checked mechanically.

- **One SecretCipher.** payment and webhook declared the same port and one class implemented both. It is now a single port in the shared kernel.
- **Ids named after aggregates.** `CredentialId`, `UserId`, `RatingId`, `PlanId` and `StoredEventId` became `ApiCredentialId`, `BillingUserId`, `UsageRatingId`, `PricingPlanId` and `StripeEventId`. A rule checks that every domain id has its aggregate beside it.
- **Events held in `*Events` classes.** `CustomerCreated` moved into `CustomerEvents` and `SubscriptionNotices` became `SubscriptionEvents`. The subscription history line, which was never a domain event, is now `SubscriptionHistoryEntry`. `UsageEvent` and `StripeEvent` keep their names: they are domain nouns, not announcements. The HTTP schema name `SubscriptionEventResponse` is unchanged because the contract is out of scope.
- **Acyclic domain features.** `UsageRated` moved to `RatingEvents`, `RefundSucceeded` to `RefundEvents` and `Quote` to the plan feature, which removed the cycles in pricing and payment. A rule keeps the domain acyclic. The application layer is deliberately not covered: the money path crosses wallet, ledger, reservation and charge on purpose.
- **Grouped shared kernel.** Eighteen flat classes became id, money, event, runtime, security and util. Spring Modulith treats a module's subpackages as internal, so verification failed on the first run. The kernel is now excluded from the Modulith modules, as `boot` already was, and ArchUnit keeps it independent.
- **Web does not touch persistence.** `RuleDto` is the JSON shape of a price rule used both to store and to serve a plan. It moved to `adapter.plan.json`, and a rule forbids web classes from importing persistence classes.

## Verification

713 tests, 0 failures, including 45 integration classes on Docker. The architecture rules grew from 27 to 34, each with a fixture that must fail it. The unit of work for every step was compile, architecture tests, then the full suite before the larger commits.

## Lessons

- The move script keeps a class in the module it started in. The merged SecretCipher landed in the webhook module and had to be moved to the kernel by hand.
- A rule that detects a type by its fully qualified name goes vacuous when the type moves. The domain-event rule matches the interface by simple name and has a test that it finds at least ten subjects.

## Next steps

- The application features still cross one another freely. If that grows beyond the money path, revisit it with the money path in mind rather than adding a rule.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
