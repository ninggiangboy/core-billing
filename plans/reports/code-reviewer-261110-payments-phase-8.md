# Code Review: Phase 8 Payments and Stripe

Scope: `billing/payment/**`, `V9__payments.sql`, `boot/config/PaymentConfiguration.java`, `boot/jobs/PaymentJobs.java`, money `ReleaseReservation`, platform `PublicEndpoint`/`Permission`, `SecurityConfiguration`, `ApiExceptionHandler`. Read-only review; build not rerun (stated green).

Ranked most severe first. Each item: location, scenario, fix.

---

## 1. HIGH: Swallowed `ConflictException` in `releaseHold` poisons the joined transaction, so a refund cannot settle once its hold has expired

- `payment/.../PaymentSettlement.java:203-212` (called at `:181` SUCCEEDED and `:192` FAILED), `RequestRefundUseCase.java:91-96`
- `SpringUnitOfWork` uses `PROPAGATION_REQUIRED`. `ReleaseReservationUseCase.execute` runs its own `unitOfWork.transactional` and throws `ConflictException` inside it when the reservation is not ACTIVE (`ReleaseReservationUseCase.java:50-51`). The inner `TransactionTemplate` sees the exception and marks the shared transaction rollback-only (`globalRollbackOnParticipationFailure` defaults to true). The `catch` swallows it, the debit and refund update run, and then the outer commit throws `UnexpectedRollbackException`.
- Scenario: a refund is requested and the 7-day hold starts (`refund-hold: P7D`, which is also `ReserveBudgetUseCase.MAX_TTL`). Stripe keeps the refund `pending` or `requires_action` for longer than 7 days, or the client retries after a `ProviderUnavailable` once the hold has gone. `ExpireReservationsUseCase` marks the hold EXPIRED. Stripe then reports `succeeded`. Every attempt rolls back, the event goes DEAD after 8 tries, the wallet is never debited, and the refund stays PENDING forever. The customer has the card refund and also keeps the balance, which they may already have spent. The same applies to the `ProviderRejected` path: the refund stays PENDING (not FAILED), the caller gets a 500 instead of a 422, and `liveAmount` keeps counting it.
- Second problem on the same path: once the hold has expired, `DebitWalletUseCase` refuses to go below spendable (`DebitWalletUseCase.java:16-18`). So even after fixing the rollback, a debit after expiry can throw `InsufficientFundsException` and get stuck the same way.
- No test covers an expired hold (`PaymentIntegrationTest` refund tests at 540-690 all settle well inside the TTL).
- Fix: add a release that does not throw (for example `ReleaseReservation.releaseIfActive(id)` that returns the status) or check the reservation status before calling. Never catch an exception thrown by a nested unit of work. For SUCCEEDED, Stripe has already moved the money, so the debit must always post: allow an overdraft or receivable for refund debits, or post it and raise a `RECONCILIATION_MISMATCH` incident. A retry loop is the wrong outcome. Also consider renewing or extending the hold while the refund is still pending at Stripe.

## 2. HIGH: Disconnecting or replacing a configuration deletes its route, which orphans in-flight payments and refunds

- `StripeConfigurationService.java:162-171` (`disconnect`) and `:173-180` (`replaceCurrent`) call `routes.remove(...)`. `ReceiveStripeWebhookUseCase.java:71-75` returns 404 for an unknown or non-live config. `:104-109` turns a Connect event into an `UNKNOWN_ACCOUNT` incident and ignores it. `ReconcileStripeUseCase.java:57` only reconciles `findLive()`.
- Scenario A: the admin rotates the Direct secret key. There is no in-place rotation, so `connectDirect` creates a new configuration and disconnects the old one. A customer still has the old Checkout page open (sessions live up to 24h) and pays, or an ACH/SEPA payment settles days later. The webhook to `/direct/{oldId}` gets 404 and reconciliation never looks at the old configuration. The customer is charged and the wallet is never credited.
- Scenario B: a refund is PENDING at Stripe when the configuration is replaced. The `refund.updated` succeeded event is dropped, the hold expires after 7 days, and the ledger is never debited (money loss). `RequestRefundUseCase.java:127-129` also blocks every refund of past payments after a key rotation.
- Fix: keep `stripe_routes` rows for disconnected configurations (add a `disconnected_at`, and make `requireAccountFree` consider only live rows). Keep accepting and processing events for objects that already exist on a disconnected configuration, and refuse only new top-ups. Reconcile any configuration that still has non-final payments or refunds. Add in-place key rotation when the verified account id is unchanged. Alternatively, refuse disconnect or replace while non-final payments or PENDING refunds exist.

## 3. HIGH: Stripe HTTP calls are made inside DB transactions that hold row locks

- `StripeEventProcessor.java:56` opens the transaction, `:63` locks the event row, and settlement locks the payment row. Inside that transaction: `PaymentSettlement.java:124` (`retrievePayment` for completed-but-unpaid checkouts), `:132` (`retrievePayment` for every intent event without our metadata), and `StripeEventProcessor.java:127` (`retrieveAccount`). Reconciliation does the same at `ReconcileStripeUseCase.java:76/105`, because `settlement.checkout` can call Stripe there too.
- Scenario: Stripe is slow (read timeout 30s and `setMaxNetworkRetries(2)`, so a call can block for over 90s). A burst of webhooks plus the retry job then holds Hikari connections and payment row locks for that long. The pool drains and every API request in the deployment stalls. This also breaks the module's own stated rule (`StripeConfigurationService` class comment: no transaction held across a network call).
- Fix: split processing into two phases. Phase 1 is a short transaction that reads the event and payment and decides whether Stripe must be consulted. Phase 2 fetches from Stripe outside any transaction. Phase 3 is a short transaction that locks again, re-checks state, and applies. Reconciliation should pre-fetch intents before opening its per-item transaction.

## 4. MEDIUM-HIGH: An event left in `RECEIVED` is never retried, and replays answer `DUPLICATE`

- `ReceiveStripeWebhookUseCase.java:144-150`: the insert commits, then `processor.attempt` runs. `JdbcStripeEventRepository.java:76` (`due`) selects only `status = 'FAILED'`.
- Scenario: the process crashes or is redeployed between the insert commit and the attempt. Or `recordFailure` itself throws, for example because no connection is available (which item 3 makes likely), or an `Error` is raised (`attempt` catches only `RuntimeException`). The event stays RECEIVED. Stripe retries, gets `DUPLICATE` with a 200, and stops. Nothing ever picks the event up. Payments within the 3-day lookback on a live configuration are eventually fixed by reconciliation. Account events, older objects, and objects on disconnected configurations are lost.
- Fix: include `status = 'RECEIVED' and received_at < now() - interval` in `due`, or insert with `status = FAILED, next_attempt_at = now() + grace`. On `DUPLICATE`, if the stored event is still RECEIVED or FAILED and due, attempt it.

## 5. MEDIUM: Stripe objects Billing did not create are deferred and retried until they go DEAD, with an incident for each

- `PaymentSettlement.java:89-92` and `:129-138` (`deferred("no payment ...")`); `:155-158` (an incident for every foreign refund); `ReceiveStripeWebhookUseCase.java:133-136` (an incident for every livemode mismatch).
- Scenario: a Direct tenant, or a Connect tenant with a full dashboard, also runs its own Stripe checkout on the same account. Every `payment_intent.*` event without `billing_payment_id` triggers `retrievePayment` (inside a transaction), is DEFERRED, and is retried 8 times. Each retry calls Stripe again and uses up the tenant's own Stripe rate limit. The event then goes DEAD and adds an `EVENT_REJECTED` incident, one per event because the subject is `event/{id}`. Also, a live Connect endpoint also receives test-mode events from connected accounts, and each one creates a separate incident. The incident list fills with noise and real problems get buried. `CreateTopUpUseCase` always stores the payment before Stripe is called, so "payment not stored yet" cannot happen for Billing's own objects.
- Fix: an object without a Billing reference, or with a reference that is not one of our payments, should be ignored (STALE) with no incident and no Stripe call. Reserve DEFERRED for genuinely transient conditions. For environment mismatches, ignore test events on live Connect without an incident, or use one aggregated subject.

## 6. MEDIUM: The unauthenticated webhook endpoints read an unbounded body into memory before the size check

- `StripeWebhookController.java:37, 50` (`@RequestBody byte[]`) and `:54-58`. The 512 KiB check runs after Spring has already buffered the whole body. Tomcat's `max-http-form-post-size` does not apply to non-form bodies, and `application.yml` sets no other limit.
- Scenario: an anonymous caller sends multi-hundred-MB POSTs to `/stripe/webhooks/connect` in parallel and the heap runs out. The comment "refused before any work is done" is not true.
- Fix: reject on `Content-Length > MAX` before reading, and read through a bounded stream (`InputStream` param plus `readNBytes(MAX+1)`). Also add a container or ingress body limit for `/stripe/webhooks/**`.

## 7. MEDIUM: A PENDING refund with no `stripe_refund_id` is never swept, and a late retry can refund twice

- `RequestRefundUseCase.java:83-106`
- Scenario: `createRefund` times out (`ProviderUnavailable`) after Stripe actually created refund R1. If R1's webhook is lost or delayed and the client retries the same key more than 24h later, Stripe's idempotency key (`refund-{id}`) has expired, so Stripe creates a second refund R2. Billing attaches R2 and R1's event is REJECTED (`PaymentSettlement.java:173-174`). The customer gets the money twice and the ledger is debited once. If nobody retries, the refund stays PENDING forever, keeps its amount in `liveAmount` (blocking further refunds), and its hold expires (see item 1).
- Fix: add a sweeper for PENDING refunds with no Stripe id. Before re-creating, list the payment intent's refunds and match `metadata.billing_refund_id`. Past the hold horizon, either fail the refund and release the hold, or extend the hold.

## 8. LOW-MEDIUM: Plaintext Stripe secrets can be logged through record `toString`

- `PaymentController.java:57` (`DirectRequest(secretKey, publishableKey, webhookSecret)`) and `:59` (`WebhookSecretRequest`) use the default record `toString`. With `logging.level.org.springframework.web=DEBUG`, Spring MVC logs `Read "application/json" to [DirectRequest[secretKey=sk_live_...` (the first 100 chars, which is most of a key). `Access` and `PaymentSettings.Connect` already override `toString`; these two records were missed.
- Fix: override `toString` on both request records so they redact the secrets.

## 9. LOW: The retry job is not safe across instances and ignores `next_attempt_at` after taking the lock

- `JdbcStripeEventRepository.java:76` / `StripeEventProcessor.java:63-66`
- Scenario: two app instances select the same due ids. The second one waits on `FOR UPDATE`, then finds the event FAILED and rescheduled by the first, and attempts it again immediately. That doubles Stripe calls and burns the 8-attempt budget faster.
- Fix: after locking, skip the event if it is FAILED and `next_attempt_at > now`. Or claim rows with `FOR UPDATE SKIP LOCKED`, as the outbox relay does.

## 10. LOW: One bad item aborts the whole reconciliation run for a tenant

- `ReconcileStripeUseCase.java:71-85`: an exception in any per-item transaction (a Stripe 5xx in `retrievePayment`, a closed wallet in `credit`, or the item 1 rollback) propagates out of `runOnce`. `TenantSweep` logs it, and every later checkout or refund for that tenant is skipped on every run. `StripePaymentProvider.java:279/300` also stops silently at 500 objects.
- Fix: catch per item, record an incident, and continue. Report truncation in the `Report`.

## 11. LOW: Bad signatures from unauthenticated callers cause a DB write per request

- `ReceiveStripeWebhookUseCase.java:96-99` (one hot platform row, subject `connect`) and `:124-127`. Every forged POST upserts and increments an incident row, so anyone can cause row-lock contention and write amplification.
- Fix: rate-limit, or sample incident writes (for example, update only when `last_seen_at` is older than 1 minute).

---

## Verified OK (relevant for risk calibration)
- The signature is verified before the body is parsed (`StripePaymentProvider.java:113-125`). The tenant comes only from `stripe_routes`, and RLS plus `belongsTo` (`PaymentSettlement.java:301-312`) block cross-tenant references in metadata.
- One credit per payment: the credit idempotency key is `topup:{paymentId}`, and concurrent events for the same payment are serialized by the payment row lock.
- Refund cap: computed under the payment lock from `liveAmount`, and backed by the `refunded_amount <= amount` check in the database.
- Lock order is payment, then refund, then wallet everywhere. The refund-only paths take a single refund lock and no other row lock afterwards, so no deadlock cycle was found.
- The columns in V9 match the JDBC repositories. The `on conflict` targets match the unique indexes (`stripe_events(stripe_account,event_id)`, the partial index `stripe_incidents_open_idx`, `payment_attempts(payment_id,stripe_event_id)`).
- Stripe SDK types appear only in `payment.adapter.stripe`. Exception text is built locally, never copied from Stripe.

## Unresolved questions
- Is disconnect or rotation meant to abandon in-flight payments? The plan does not say. Item 2 assumes not.
- Should a refund debit that Stripe has already confirmed be allowed to overdraw a Prepaid wallet? That is a product decision needed for the item 1 fix.

Status: DONE
