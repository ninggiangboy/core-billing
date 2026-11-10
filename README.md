# Core Billing

Documentation lives in [docs/](docs/README.md). Code lives in:

| Directory | Contents |
|---|---|
| `billing/` | Core Billing System (Gradle multi-module, Spring Boot) |
| `sample-tenant/` | Sample Tenant System, built and deployed independently |
| `web/` | Web apps (Platform Admin, Tenant Admin, Customer Portal, Sample Tenant UI) |
| `contracts/` | OpenAPI and event schemas shared between systems as documents, never as code |

Build the backend: `cd billing && ./gradlew build` (needs JDK 25 and a running Docker daemon for Testcontainers).

Run the app locally:

```bash
cp .env.example .env            # then replace every placeholder value
docker compose up -d            # PostgreSQL on :5432, Keycloak on :8081 (imports ops/keycloak/realm-export.json)
set -a; source .env; set +a     # the application reads these variables
cd billing && ./gradlew :app-boot:bootRun
```

The application connects as the unprivileged `corebilling_app` role; Flyway migrates as the table owner. `BILLING_BASE_DOMAIN` (default `localhost`) makes `platform.<base>` the Platform and `<tenant-code>.<base>` a Tenant; `*.localhost` resolves to the local machine in current browsers. Set `BILLING_BOOTSTRAP_ADMIN_EMAIL` to create the first Platform administrator on start. After an intended API change, regenerate the contract with `./gradlew :app-boot:test --tests '*OpenApiContractTest' -Dopenapi.update=true`.
