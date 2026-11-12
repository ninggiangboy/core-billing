# tenant

A reference Cloud Resource Management tenant. It is its own Gradle build and database and
integrates with Core Billing only over REST and signed webhooks; it shares no code with
`billing/` and builds against the technical `core/` beside it. See [docs/tenant-system.md](../docs/tenant-system.md).

## Build and run

```bash
# build and run the architecture gates
./gradlew checkNoBillingDependency build

# start a database on :5433, then the service on :8082
docker compose -f ../docker-compose.yml up -d tenant-postgres
SAMPLE_TENANT_BILLING_URL=http://localhost:8080 \
SAMPLE_TENANT_BILLING_API_KEY=cbk_... \
SAMPLE_TENANT_BILLING_SYSTEM_ID=<tenant-system-id> \
SAMPLE_TENANT_WEBHOOK_SECRET=whsec_... \
./gradlew :apps:app-boot:bootRun
```

The `billing/` deployment is only a dependency at runtime. With Billing stopped the service
still starts and serves registration (the mapping stays `PENDING`), login, users, the catalog
and webhook verification. Actions that need Billing fail with a clear `502` and leave no order
behind.

### Bootstrap

Set `SAMPLE_TENANT_BOOTSTRAP_ENABLED=true` and `SAMPLE_TENANT_BOOTSTRAP_ADMIN_TOKEN` to an
administrator's OIDC bearer token. On start the bootstrap publishes one Billing pricing plan per
catalog plan that has no binding, binds the returned ids by plan code, and registers
`SAMPLE_TENANT_WEBHOOK_URL` once. It logs the endpoint's signing secret once; store it as
`SAMPLE_TENANT_WEBHOOK_SECRET`. The command is safe to repeat.

### Build convention

Each module is a Gradle project whose `api`, `domain`, `application` and `adapter` layers are
separate source sets, so Spring cannot leak into the plain-Java inner layers. Packages are grouped
by feature and then by role (`...command`, `...query`, `...port`, `...service`), adapters by
`adapter.<feature>.<technology>`, and `api` by the capability a caller uses (`lookup`, `execution`,
`recording`). `./gradlew checkNoBillingDependency` fails if any module declares a Gradle dependency
on Billing (the shared `core/` is allowed), and `ArchitectureTest`, its fixture meta-test and
`ModulithVerificationTest` re-check the same boundaries and package shape on the classpath.

### Persistence

Writes go through a `Jooq*Repository` in `adapter.<feature>.persistence`; reads select straight into
immutable views through a `Jooq*ReadRepository` in the same package, and controllers render views.
`libs:jooq-schema` holds the jOOQ tables and records generated from the Flyway migrations and
committed, so a normal compile needs no database. `JooqSchemaTest` migrates a throwaway PostgreSQL,
regenerates the schema and fails when the committed code is out of date:

```bash
./gradlew :apps:app-boot:test --tests '*JooqSchemaTest' -Djooq.update=true
```

`Jooq*RepositoryIntegrationTest` and the HTTP tests need Docker (one PostgreSQL 18 container per
test JVM) and never call Billing: they replace every Billing client with `FakeBilling`.

## Scenario-to-test matrix

| # | Scenario | Module | API | Test |
|---|---|---|---|---|
| 1 | Registration and Customer Mapping | `identity`, `customer` | `POST /api/auth/register`, `GET /api/customers/me` | `RegisterUserUseCaseTest`, `RegisterCustomerUseCaseTest` |
| 2 | Fetch Pricing Plan and estimate cost | `catalog`, `resource` | `GET /api/catalog/plans`, `POST /api/orders` | `CreateOrderUseCaseTest` |
| 3 | Check Wallet and reserve | `resource` | `POST /api/orders`, `GET /api/billing/wallets` | `CreateOrderUseCaseTest` |
| 4 | Create order and enqueue only after the reservation | `resource` | `POST /api/orders` | `CreateOrderUseCaseTest` |
| 5 | Record usage during execution | `usage`, `execution` | `GET /api/usage`, Billing `POST /api/v1/usage-events` | `RecordUsageUseCaseTest`, `ExecuteOrdersUseCaseTest` |
| 6 | Charge when cost is incurred | `execution` | Billing `POST /api/v1/reservations/{id}/capture` | `ExecuteOrdersUseCaseTest` |
| 7 | Release the remaining reservation | `execution` | Billing `POST /api/v1/reservations/{id}/release` | `ExecuteOrdersUseCaseTest` |
| 8 | Top up through Stripe | `customer` | `POST /api/billing/session`, portal redirect | `CustomerBillingQueriesTest`, `OpenBillingSessionUseCaseTest`, `TopUpUseCaseTest` |
| 9 | Stripe Direct and Connect unchanged | `customer` | same session call for both | `CustomerBillingQueriesTest`, `OpenBillingSessionUseCaseTest`, `TopUpUseCaseTest` |
| 10 | View invoices and transaction history | `customer` | `GET /api/billing/invoices`, `GET /api/billing/payments` | `CustomerBillingQueriesTest`, `OpenBillingSessionUseCaseTest`, `TopUpUseCaseTest` |
| 11 | Handle Billing webhooks | `webhook` | `POST /webhooks/billing` | `ReceiveWebhookUseCaseTest`, `WebhookSignatureTest` |
| 12 | Subscriptions and combined pricing | `resource`, `execution`, `webhook` | `POST /api/orders` (Managed Service, Cloud Package), subscription webhooks | `CreateOrderUseCaseTest`, `ExecuteOrdersUseCaseTest`, `ReceiveWebhookUseCaseTest` |
| 13 | Prepaid and Postpaid customers | `customer` | `PUT /api/customers/me/wallet-settings` | `ManageWalletSettingUseCaseTest` |

Scenarios 14 (data isolation between tenants) and 15 (the same email in independent tenants) are
exercised by the phase 14 end-to-end suite with a second tenant.

## API surface

| Group | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout` |
| Users | `GET /api/users/me`, `GET/POST /api/users`, `PUT /api/users/{id}/role`, `POST /api/users/{id}/deactivate` |
| Customers | `GET /api/customers/me`, `GET /api/customers`, `POST /api/customers/{id}/mapping`, `GET/PUT /api/customers/me/wallet-settings` |
| Catalog | `GET /api/catalog/plans`, `GET /api/catalog/plans/{id}`, `GET /api/catalog/resource-types` |
| Orders | `POST/GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders/{id}/cancel` |
| Usage | `GET /api/usage` |
| Billing views | `POST /api/billing/session`, `GET /api/billing/wallets`, `GET /api/billing/invoices`, `GET /api/billing/payments`, `POST /api/billing/top-ups` |
| Webhooks | `POST /webhooks/billing` |

## Configuration

| Property | Meaning |
|---|---|
| `SAMPLE_TENANT_DB_URL`, `SAMPLE_TENANT_DB_USER`, `SAMPLE_TENANT_DB_PASSWORD` | This system's own database |
| `SAMPLE_TENANT_BILLING_URL`, `SAMPLE_TENANT_BILLING_API_KEY`, `SAMPLE_TENANT_BILLING_SYSTEM_ID` | The Billing deployment and this Tenant System's credential |
| `SAMPLE_TENANT_WEBHOOK_SECRET` | Signing secret for inbound Billing webhooks |
| `SAMPLE_TENANT_EXECUTION_ENABLED`, `SAMPLE_TENANT_EXECUTION_POLL_DELAY` | The execution worker |
| `SAMPLE_TENANT_BOOTSTRAP_ENABLED`, `SAMPLE_TENANT_BOOTSTRAP_ADMIN_TOKEN` | One-shot reference provisioning |
| `SAMPLE_TENANT_PORTAL_RETURN_URL` | Default return URL for a Billing Session |
