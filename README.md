# Core Billing

Documentation lives in [docs/](docs/README.md). Code lives in:

| Directory | Contents |
|---|---|
| `billing/` | Core Billing System (Gradle multi-module, Spring Boot) |
| `sample-tenant/` | Sample Tenant System, built and deployed independently |
| `web/` | Web apps (Platform Admin, Tenant Admin, Customer Portal, Sample Tenant UI) |
| `contracts/` | OpenAPI and event schemas shared between systems as documents, never as code |

Build the backend: `cd billing && ./gradlew build` (needs JDK 25 and a running Docker daemon for Testcontainers).

Run the app locally: `docker compose up -d postgres`, then `cd billing && ./gradlew :app-boot:bootRun`. The application connects as the unprivileged `corebilling_app` role (`BILLING_DB_URL`, `BILLING_DB_USER`, `BILLING_DB_PASSWORD`); Flyway migrates as the table owner (`BILLING_MIGRATION_DB_USER`, `BILLING_MIGRATION_DB_PASSWORD`). Defaults match `docker-compose.yml`. `BILLING_BASE_DOMAIN` (default `localhost`) makes `platform.<base>` the Platform and `<tenant-code>.<base>` a Tenant.
