---
title: Core Billing implementation plan
date: 2026-11-09
summary: 14-phase plan from build foundation to 15 end-to-end scenarios
---

# Core Billing implementation plan

Created plans/261109-1625-core-billing-implementation with 14 phases (about 76 days): foundation and enforcement, tenancy and RLS, identity, customers, money, outbox and webhooks, pricing and usage, Stripe, invoicing, billing session, sample tenant backend, web apps, end-to-end and docs.

Findings that shaped the plan:
- One Gradle module holding domain, application and adapter cannot ban Spring from only two layers on a single classpath. Phase 1 spikes three source sets per module and keeps ArchUnit plus a grep gate as the fallback.
- Spring Modulith discovery across Gradle modules sharing a root package is unverified; phase 1 spikes it.
- Charge from usage needs a read-only synchronous edge from pricing to usage.api, added to the money path in the architecture ledger in the final phase.

Assumptions awaiting confirmation: single repo layout, pnpm workspace of four apps, Keycloak username {tenant-code}:{email}, refund of top-ups limited to unspent balance, Tailwind with shadcn/ui.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
