---
title: "One source for event type, inbox consumer and system actor names"
description: "Publish the event type names other contexts consume from the producer's api package, and gather inbox consumer and system actor names into one class per context."
status: completed
priority: P2
effort: 1.5h
branch: dev
tags: [backend, refactor]
created: 2026-11-11
---

# One source for event type, inbox consumer and system actor names

## Outcome, constraints, non-goals, acceptance

- **Outcome.** Invoicing no longer copies the event type strings it reacts to. `charge.succeeded`,
  `payment.succeeded` and `payment.failed` each have one source, in the `api` package of the
  context that announces them. Each context has one class that lists its inbox consumer names and
  one that lists its system actor names, so a reader finds them in one file.
- **Constraints.**
  - Every string value stays byte-identical. Consumer names are part of the inbox key
    `(tenant_id, consumer, event_id)`; actor names are stored in `ledger_entries.actor` and
    `audit_log.actor`. A renamed consumer would re-handle every event still in the outbox.
  - A context reaches another only through its `api` package (`no-cross-context-import`). The
    `api` package names only the JDK, the shared kernel and other `api` packages.
  - Application classes sit under `<feature>.<role>` (`role-package-not-layer-root`,
    `application-classes-in-a-role-package`). The new classes go in
    `<ctx>.application.messaging.service`; no ArchUnit rule is changed.
  - Constants stay `public static final String`; the ports (`EventInbox.firstTime`, `AuditEntry`,
    `LedgerEntry.Origin`, `Audit`) keep taking `String`.
  - No plan or phase identifiers in code, test names or commits.
- **Non-goals.** Changing any string value. Editing literals in tests (they are the independent
  check of the contract). A shared-kernel event catalog. Moving the `TYPE` of events no other
  context consumes. Turning the constants into enums. Changing ArchUnit or Modulith rules. A
  uniqueness test for consumer names.
- **Acceptance.**
  - `grep -rnE 'static final String (CONSUMER|EVENT_TYPE|ACTOR)\b' billing/contexts` prints nothing,
    and `ApplyPaymentUseCase` declares no `SUCCEEDED` or `FAILED`, and
    `BootstrapPlatformAdminUseCase` no longer spells `"system/bootstrap"`.
  - `grep -rnE '"(charge\.succeeded|payment\.(succeeded|failed)|system/[a-z-]+|invoicing\.(cycle|payment)|webhook)"' billing/contexts/*/src/{domain,application}`
    matches only the new `*EventTypes`, `*Consumers` and `*Actors` classes.
  - `cd billing && ./gradlew test` is green, including `ArchitectureTest` and
    `ModulithVerificationTest`. `contracts/` is unchanged.

## Phases

| # | Phase | Depends on | Status |
|---|-------|------------|--------|
| 1 | [Event type names in the producer api](./phase-01-event-type-names.md) | none | completed |
| 2 | [Consumer and actor names per context](./phase-02-consumers-and-actors.md) | 1 | completed |

Each phase is one focused commit:

1. `refactor(billing): publish consumed event type names from the producer api`
2. `refactor(billing): gather inbox consumers and system actors per context`

## Risks

- **A value changes during the move.** Copy each string from the line being deleted, and check the
  acceptance grep plus `git diff` for any changed literal before committing.
- **The new `messaging` package trips an ArchUnit rule.** By reading the rules it passes: the
  package has a `service` role segment, holds no use case and implements no `api` interface. If a
  rule fails anyway, stop and ask instead of changing the rule.

## Rollback

Both phases are pure refactors with no schema or contract change. Revert the commit.
