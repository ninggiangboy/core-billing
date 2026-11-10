---
phase: 3
title: "Layout: pricing, payment, money"
status: pending
priority: P1
effort: "1.5d"
dependencies: [2]
---

# Phase 3: Layout: pricing, payment, money

## Goal

Apply the layout recorded in phase 2 to the three modules that carry the money path and the most files.

## Context

Pure move, as in phase 2. Suggested feature groups (confirm against the files before moving; record the final mapping in the phase notes):

- `money`: `wallet`, `ledger`, `reservation`, `charge`, plus `event` for `MoneyEvents` in `domain`. Application split: `wallet/command` (`OpenWalletUseCase`, `CreditWalletUseCase`, `DebitWalletUseCase`), `wallet/query` (`WalletQueries`, `WalletLookupService`), `ledger/command` (`ReverseEntryUseCase`, `VerifyLedgerUseCase`), `reservation/command` (reserve, release, expire), `charge/command` (`ChargeUseCase`), each feature's `port/` for its repository. `LedgerPosting` and `RequestFingerprint` go to the feature that uses them most.
- `payment`: `payment` (Payment, attempt, id, status), `refund`, `stripe` (configuration, environment, events, routes, webhook receive and reconcile use cases), `incident`. `PaymentProvider`, `ProviderAccess`, `SecretCipher` are ports; `ProviderRejectedException`, `ProviderUnavailableException`, `InvalidSignatureException` live with the feature that throws them.
- `pricing`: group by aggregate (plan, metric, rating); list the 25 domain files first.

`money.api` (23 types) splits into `wallet`, `reservation`, `charge`, `ledger`; keep each command, result and id next to its feature, and keep `InsufficientFundsException` with the wallet operations that throw it.

## Files to Create / Modify

- Move with `git mv`, then fix `package` and `import` lines, for every file under `billing/pricing`, `billing/payment` and `billing/money`, all source sets including `src/test`.
- Modify imports in `billing/app-boot` (`PricingConfiguration`, `PaymentConfiguration`, `MoneyConfiguration`, jobs, tests) and in other modules that use `money.api`, `pricing.api` or `payment.api`.
- Modify each module's `adapter/.../api/package-info.java` following the pattern recorded in phase 2.

## Tasks & Steps

- [x] 1. Write the file-to-package mapping for `pricing` in the phase notes.
- [x] 2. Move `pricing` (smallest), compile, fix imports, run `./gradlew :pricing:test`.
- [x] 3. Move `payment`, compile, fix imports, run `./gradlew :payment:test`.
- [x] 4. Move `money`, compile, fix the roughly 180 import lines that reference `money.api`, run `./gradlew :money:test`.
- [x] 5. Add the three modules to the layout set, run the architecture tests.
- [x] 6. One commit per module: `refactor(<module>): group packages by feature and role`.

## Phase notes

### pricing

- `pricing.domain.plan`: PricingPlan, PlanId, PlanStatus, PlanVersion, BillingCycle, PriceRule, PriceLine, Prices, RoundingPolicy, Tier, TierMode, PricingModel, Subscription, EventBased, Hybrid, TimeBased, UsageBased, WorkloadBased, PricingEvents.
- `pricing.domain.metric`: Measure, UsageSummary.
- `pricing.domain.rating`: RatingId, RatingStatus, UsageRating, Quote.
- `pricing.application.plan.command`: PlanAdministration; `.plan.port`: PlanRepository.
- `pricing.application.metric.query`: MetricCatalogService; `.metric.port`: MetricRegistry.
- `pricing.application.rating.command`: ChargeFromUsageUseCase, EstimateCostUseCase, ReserveFromEstimateUseCase, PlanSelection (package-private, used by both rating use cases); `.rating.query`: RatingQueries; `.rating.port`: RatingRepository.
- `pricing.application.shared.command`: Audit.
- `pricing.api.metric`: MetricCatalog, with `@NamedInterface("pricing-metric")`.
- `adapter`: RuleDto moved from the `adapter` root to `adapter.persistence` (it is shared with the web adapter); all repositories stay in `adapter.persistence`.

### payment

- `payment.domain.payment`: Payment, PaymentAttempt, PaymentId, PaymentStatus, PaymentEvents.
- `payment.domain.refund`: Refund, RefundId, RefundStatus.
- `payment.domain.stripe`: StripeConfiguration, StripeConfigurationId, StripeEnvironment, StripeEvent, StoredEventId, EventStatus, EventAttempt, ConnectionStatus, IntegrationMode.
- `payment.domain.incident`: Incident, IncidentKind.
- `payment.application.payment.command`: CreateTopUpUseCase, PaymentSettings; `.payment.query`: PaymentQueries; `.payment.port`: PaymentRepository, PaymentProvider.
- `payment.application.refund.command`: RequestRefundUseCase; `.refund.port`: RefundRepository.
- `payment.application.stripe.command`: ReceiveStripeWebhookUseCase, ReconcileStripeUseCase, RetryStripeEventsUseCase, StripeConfigurationService, StripeEventAdministration, StripeEventProcessor, InvalidSignatureException, ProviderRejectedException, ProviderUnavailableException; `.stripe.port`: StripeConfigurationRepository, StripeEventRepository, StripeRoutes, ProviderAccess, SecretCipher.
- `payment.application.incident.port`: IncidentRepository.
- `payment.application.shared.command`: Audit, Fingerprint, PaymentSettlement.

### money

- `money.domain.wallet`: Wallet; `.ledger`: LedgerEntry; `.reservation`: Reservation; `.charge`: Charge, ChargeService; `.event`: MoneyEvents.
- `money.api.wallet`: CreditCommand, CreditWallet, DebitCommand, DebitWallet, InsufficientFundsException, WalletLookup, WalletMode, WalletSummary.
- `money.api.reservation`: ReleaseReservation, ReservationId, ReservationResult, ReservationStatus, ReserveBudget, ReserveBudgetCommand.
- `money.api.charge`: ChargeCommand, ChargeId, ChargeResult, ChargeSource, ChargeStatus, ChargeWallet.
- `money.api.ledger`: EntryType, LedgerEntryId, LedgerPostingResult.
- `money.application.wallet.command`: OpenWalletUseCase, CreditWalletUseCase, DebitWalletUseCase, RequestFingerprint; `.wallet.query`: WalletQueries, WalletLookupService; `.wallet.port`: WalletRepository.
- `money.application.ledger.command`: ReverseEntryUseCase, VerifyLedgerUseCase, LedgerPosting, LedgerVerifier; `.ledger.port`: LedgerRepository.
- `money.application.reservation.command`: ReserveBudgetUseCase, ReleaseReservationUseCase, ExpireReservationsUseCase; `.reservation.port`: ReservationRepository.
- `money.application.charge.command`: ChargeUseCase; `.charge.port`: ChargeRepository.
- API sub-packages get `@NamedInterface("money-wallet" | "money-reservation" | "money-charge" | "money-ledger")` in the adapter source set.

### Deviations recorded

- `LedgerPosting`, `RequestFingerprint`, `PaymentSettlement.releaseHold` and `Fingerprint` were package-private and are used by more than one feature. The plan puts each shared helper in the feature that uses it most; that requires making the helper and the used methods `public`. This is the only visibility change in the phase, and there is no logic change.
- `PlanSelection` stays package-private because both its users (ChargeFromUsageUseCase, EstimateCostUseCase) are in `rating.command`.
- Adapter helpers (`Rows`) stay in `adapter.persistence` for each module because several feature repositories use them; adapter packages are not split by feature in this phase.

### Result

- `./gradlew build` green after each module and at the end; 551 tests, 0 failures (same count as phase 1).
- `ArchitectureTest` and `ModulithVerificationTest` pass with `pricing`, `payment` and `money` in the layout set and the split `money.api` / `pricing.api.metric` named interfaces.

## Verification

- `cd billing && ./gradlew build` is green; the Testcontainers money, payment and pricing integration tests pass; test count equals the baseline.
- `ModulithVerificationTest` passes with the split `money.api`.
- Rename-only diff, as in phase 2.

## Risks and rollback

- Package-private helpers (`Rows`, row records) become unreachable if a repository and its helper land in different packages. Keep a helper with the repository that uses it, or duplicate nothing and make the shared helper a package-private class in each feature that needs it only if it is tiny. Do not widen visibility to `public` just to compile.
- Rollback is reverting the module's move commit.
