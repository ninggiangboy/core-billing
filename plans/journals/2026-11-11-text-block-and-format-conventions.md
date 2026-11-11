---
title: "Text blocks and formatted() across the billing modules"
date: 2026-11-11
summary: "SQL and test JSON became multi-line literals, multi-value messages moved to formatted(); the hard part was agreeing which strings should keep +."
---

# Text blocks and formatted() across the billing modules

## What happened

Two hundred string concatenations across eighty files were reviewed against one rule: the tool follows the shape of
the string, not a blanket ban on `+`. Every adapter SQL statement is now a single text block, the shared column lists
go in through `%s` + `formatted()`, the Stripe test JSON is a properly formatted multi-line payload, and messages that
insert two or more values use `formatted()` on one literal. The rule and its three traps are recorded in
`docs/architecture.md` under "Text conventions". Full `./gradlew build` is green.

Committed by group: the wire-test JSON first, then one commit per module for the adapters (webhook, payment, money,
invoicing, usage, customer, pricing), then the message conversions, then the docs.

## Problems worth remembering

- `formatted()` turns the first `%` it finds into a conversion. A SQL `like '%Insufficient%'` or a pattern built as
  `"%" + secret + "%"` cannot go through it without doubling every `%`. The codebase has three such literals, all
  left alone.
- A text block always ends with a newline. Harmless in SQL and JSON, but it changes a message stored in
  `stripe_incidents.detail`. A one-line message therefore stays a plain literal; wrapping it in a text block with a
  trailing `\` works but reads worse than the string it replaced.
- Wrapping JSON in a text block is not the same as formatting it. The first attempt put the payload in a block but
  kept it on one line, which hides the same structure the change was meant to reveal.
- A helper that inserts a JSON object into a template cannot produce correctly indented output: text-block stripping
  is computed per literal, so the inserted fragment starts at column zero. The event fixtures were written out in
  full instead, and the helper went away.
- Single-line JSON fragments in `assertThat(...).contains(...)` must keep their `\"`: a text block would append a
  newline the assertion would have to match.

## Decisions

- SQL composed from a shared column list uses `%s` + `formatted()`; the column-list constant itself keeps `+`,
  because a text block would put newlines inside the list.
- Identifiers and subjects (`"wallet/" + id`, `"event/" + id`) and line-item text keep `+`; they compose a value, not
  a sentence.
- The convention is documented for review only. Unlike the layer rules, nothing in the build enforces it.

## Next steps

- Watch whether the docs-only rule holds. The first violation merged without a review comment is the signal to add a
  source-scanning test, which the build does not have today.
- `String.format` remains the tool for fixed-width numbers only; if a second locale-sensitive format appears, revisit
  whether `%s` everywhere is still the right default.
- The `%` trap deserves a test if any query ever grows a `like` pattern.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
