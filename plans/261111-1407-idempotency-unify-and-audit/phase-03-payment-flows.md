---
phase: 3
title: "Payment: top-up, invoice payment, refund"
status: completed
priority: P1
effort: "3h"
dependencies: [1]
---

# Phase 3: Payment: top-up, invoice payment, refund

## Goal

Racing duplicates of a top-up, an invoice payment or a refund replay the first request instead of getting a 409.

## Context

- `CreateTopUpUseCase.prepare` and `CreateInvoicePaymentUseCase.prepare` read the key and then call
  `payments.add` with no lock. The loser hits `payments_tenant_id_idempotency_key_key`; `JooqPaymentRepository.add`
  has no catch, so `ApiExceptionHandler.integrity` answers a generic 409, and the client never gets the checkout URL.
- `RequestRefundUseCase.prepare` reads the key at line 142 and only then locks the payment at line 151. The loser
  waits on the lock but does not read the key again. A full refund then reports "at most 0 can still be refunded";
  a partial one creates a hold and fails on the key constraint. The refund hash already includes `paymentId`.
- Controllers already answer 201 for new and 200 for replay.

## Files to Create / Modify

- Modify: `billing/contexts/payment/src/application/.../payment/port/PaymentRepository.java` (`addIfAbsent` replaces `add`; `add` has no other caller)
- Modify: `billing/contexts/payment/src/adapter/.../payment/persistence/JooqPaymentRepository.java`
- Modify: `billing/contexts/payment/src/application/.../payment/command/CreateTopUpUseCase.java`
- Modify: `billing/contexts/payment/src/application/.../payment/command/CreateInvoicePaymentUseCase.java`
- Modify: `billing/contexts/payment/src/application/.../refund/command/RequestRefundUseCase.java`
- Modify: `billing/contexts/payment/src/adapter/.../refund/persistence/JooqRefundRepository.java`
- Modify tests: `PaymentIntegrationTest`, `InvoicePaymentIntegrationTest` (in `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/`)

## Tasks & Steps

- [x] 1. `JooqPaymentRepository.addIfAbsent`: the existing insert plus `on conflict (tenant_id, idempotency_key) do nothing`,
      returning `execute() == 1`.
- [x] 2. Top-up and invoice payment: keep the read-first fast path, then call `addIfAbsent`. On `false`, read the
      winner and run the same replay branch as the fast path (hash check through `requireSameRequest`, then the
      configuration check). Write the audit entry only on a real insert. Extract the replay branch into one private
      method per use case, so both paths share it.
- [x] 3. Refund: lock the payment first, then read the key. Keep the existing replay branch and `RETRY_WINDOW` rule.
      Lock order stays payment, then refund, then wallet. Update the "Lock order" comment.
- [x] 4. `JooqRefundRepository.add`: when `Causes.violates(e, REFUNDS_TENANT_ID_IDEMPOTENCY_KEY_KEY.getName())`, throw
      `IdempotencyConflictException("refund", key)`. Only a same key on a different payment, which takes a different
      lock, reaches this.
- [x] 5. Tests:
       - 8 parallel top-ups with one key give one payment row, one checkout session opened at the fake provider, one
         201 and seven 200s with the same payment id and checkout URL.
       - The same for an invoice payment.
       - 8 parallel full refunds with one key give one refund row and one hold, and every response has the same
         refund id. No "at most 0" message.
       - The same refund key on two different payments, sent concurrently, gives one 201 and one 409 with the
         idempotency-conflict message.

## Verification

- `./gradlew :contexts:payment:test`
- `./gradlew :apps:app-boot:test --tests '*Payment*' --tests '*Refund*' --tests '*InvoicePayment*'`

## Risks and rollback

The fake payment provider must count checkout openings for the first test. If it does not, add a counter to
`billing/apps/app-boot/src/test/java/dev/ngb/core_billing/support/FakePaymentProvider.java`. Code-only change:
revert the commit.
