---
title: Migrate tenant billing-client to Spring Cloud OpenFeign
date: 2026-11-12
summary: "Replace HttpBillingClient with an OpenFeign adapter under Boot 4.1.2, keeping retry, idempotency and domain error mapping, with the review fixes recorded."
---

# Migrate tenant billing-client to Spring Cloud OpenFeign

## What happened
- Replaced the hand-written `HttpBillingClient` in `tenant/modules/billing-client` with a Spring Cloud OpenFeign adapter.
- Added Spring Cloud BOM `2025.1.4` (OpenFeign `5.0.4`, Feign `13.6.1`) and `feign-java11` to the adapter layer; versions live in `tenant/gradle/libs.versions.toml`.
- New package `dev.ngb.tenant.billing.adapter.client.feign`:
  - `BillingFeignApi` (package-private) holds every path/query/header via Spring MVC annotations, including optional query params that Feign drops when null.
  - `FeignBillingClient` implements the 11 `Billing*` api interfaces, rejects a null `Idempotency-Key` on every keyed write before any request, and maps an exhausted `RetryableException` to `UpstreamUnavailableException`.
  - `BillingErrorDecoder`, `LinearRetryer`, `BearerAuthInterceptor`, `JacksonCodec`, `BillingFeignClientFactory`.
- `BillingConfiguration` and `BootstrapRunner` now build clients through the factory; `BootstrapRunner` still creates one admin-token client and one tenant-key client.
- Deleted `HttpBillingClient` and `HttpBillingClientTest`; source `grep -r HttpBillingClient tenant/` returns nothing.

## Verification
- `FeignBillingClientTest` (14 tests) covers credential, Idempotency-Key on writes, same-key retry, retry exhaustion, 401/402/403/404/409/4xx mapping, query/path shape, null optional-param omission, null-key refusal, encoded slash in a path value, unknown JSON fields, Content-Type/Accept.
- `./gradlew build --rerun-tasks` in `tenant/` passes, including ArchUnit, Spring Modulith and the Testcontainers integration tests; `FeignBillingClientTest` has 14 tests.
- OpenFeign 5.0.4 works on Spring Boot 4.1.2 with Jackson 3; the previously unverified compatibility risk is resolved.

## Review fixes
- A null `Idempotency-Key` used to be dropped silently and the write retried without protection; the old client failed with a `NullPointerException`. The client now fails fast with a clear message, and a test asserts no request is sent.
- Feign decodes an encoded slash in a path value back into `/`, so `by-external-user/a/b` reached a different path. `BillingFeignClientFactory` now uses a `SpringMvcContract` subclass that sets `decodeSlash(false)`; a test asserts `a/b c` is sent as `a%2Fb%20c`.
- Two behaviors differ from the old client by design: an exhausted 5xx or 429 now carries the status and detail only in the cause (message "Billing could not be reached"), and an exhausted 429 is an `UpstreamUnavailableException` instead of a `BadRequestException`.
- `spring-cloud-openfeign-core` alone was tried in place of the starter and rejected: `FeignAutoConfiguration` still loads and the application context fails to start without the starter's extra libraries. The starter stays.

## Decision
- Used `Feign.builder()` rather than `FeignClientBuilder`: it needs no `ApplicationContext`/`FeignClientFactory`, keeps the adapter unit-testable against the fake `HttpServer`, and lets each credential get its own proxy. The Spring Cloud `SpringMvcContract` still interprets the annotations.
- Wrote a Jackson 3 `Encoder`/`Decoder` over the same `JsonMapper` config (`FAIL_ON_UNKNOWN_PROPERTIES = false`) to keep decoding behavior identical rather than depending on Boot's converter wiring.
- Used `Http2Client` explicitly instead of the default `HttpURLConnection`, and a linear `Retryer` (backoff × attempt) instead of `Retryer.Default`.
- New client code: `BillingFeignApi` + `FeignBillingClient` + error decoder + retryer + interceptor = 449 lines (under the 472 target).

## Next steps
- Historical plan files under `plans/261112-0340-...` still mention `HttpBillingClient` as the layout at that time; left untouched because they record a past decision.
- No API contract, Boot upgrade, circuit breaker or load balancer changes were made.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
