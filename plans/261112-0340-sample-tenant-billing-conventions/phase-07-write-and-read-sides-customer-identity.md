---
phase: 7
title: "Write and read sides: customer, identity"
status: completed
priority: P1
effort: "1.5d"
dependencies: [6]
---

# Phase 7: Write and read sides: customer, identity

## Goal

Apply the phase 6 pattern to `customer` and `identity`, the modules every other module depends on. Authentication lookups stay on write ports, as in Billing.

## Modules

| Module | Write side | Read side | Special |
|---|---|---|---|
| customer / customer | `CustomerRepository`: `save` (upsert), `findById`, `findByUserId` (use cases and the listener decide with them) | `CustomerReadRepository.list`, `CustomerView`; `CustomerQueries`; `CustomerDirectoryService implements CustomerDirectory` reads through it | `CustomerDirectory` and `CustomerSnapshot` are `api` types; their shape does not change |
| customer / wallet | `WalletSettingRepository`: `findByCustomerId` and `save` (`on conflict (customer_id) do update`) | `WalletSettingView` only if a controller needs it beyond the write port; otherwise none | upsert stays an explicit statement |
| identity / user | `UserRepository`: `findById`, `findByEmail`, `emailExists`, `save` (upsert) | `UserReadRepository.list`, `UserView`; `UserController` maps from it | `findByEmail` and `emailExists` are the login and registration path: keep them on the write port and on the current request path |
| identity / session | `SessionRepository`: `save`, `findByTokenHash`, `deleteByTokenHash`, `deleteExpired` (bulk `deleteFrom`) | none | `findByTokenHash` is called on every authenticated request by `SessionAuthenticationFilter`; keep it one indexed select and compare `EXPLAIN` before and after |

The bulk `deleteExpired` and the upserts stay explicit statements, never `UpdatableRecord.store()`.

## Files

- Modify/create/delete: `sample-tenant/modules/{customer,identity}/src/{application,adapter,test}/**`
- Modify: `sample-tenant/apps/app-boot/src/main/java/dev/ngb/sampletenant/boot/config/{Customer,Identity}Configuration.java`
- Modify: `sample-tenant/apps/app-boot/src/main/java/dev/ngb/sampletenant/boot/security/SessionAuthenticationFilter.java` only if its dependencies change names

## Tasks and steps

1. Scout both modules: which callers use `findAll`, `findByEmail`, and the customer lookups; record the read and write use of each port method before splitting.
2. Customer first (one commit per feature), then identity (user, then session). Pattern steps 1 to 7 of phase 6.
3. `CustomerAccountListener` (customer adapter, implements `identity.api.account.AccountListener` from phase 1) keeps calling the two use cases; no persistence change.
4. Session path check: run `EXPLAIN (ANALYZE)` of the `user_sessions` lookup before and after on a database with a few thousand sessions; record the result in the commit message. Read performance is not a goal, only no regression.

## Verification

- `cd sample-tenant && ./gradlew checkNoBillingDependency build` green with Docker.
- Phase 5 HTTP tests for auth, users, customers and wallet settings pass without assertion edits.
- `grep -rln 'JdbcTemplate\|@Table\|JdbcRepository' sample-tenant/modules/{customer,identity}` returns nothing.

## Risks

- Authentication is on the hot path of every request; the session lookup stays a single statement and keeps its index.
- Customer and identity are intertwined at registration (user saved, then customer created, not atomic today). This plan does not add atomicity (non-goal: new use-case transactions); note it for the user if a test makes the gap visible.

## Todo

- [x] customer (customer, wallet) migrated
- [x] identity (user, session) migrated
- [x] Session lookup compared before and after
