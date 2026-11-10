# Core Billing

Documentation lives in [docs/](docs/README.md). Code lives in:

| Directory | Contents |
|---|---|
| `billing/` | Core Billing System (Gradle multi-module, Spring Boot) |
| `sample-tenant/` | Sample Tenant System, built and deployed independently |
| `web/` | Web apps (Platform Admin, Tenant Admin, Customer Portal, Sample Tenant UI) |
| `contracts/` | OpenAPI and event schemas shared between systems as documents, never as code |

Build the backend: `cd billing && ./gradlew build` (needs JDK 25 and a running Docker daemon for Testcontainers).
