# contracts

Documents that the two systems share, never code. The only code they share is the technical `core/`.

| Path | Owns |
|---|---|
| `openapi/billing.yaml` | The REST API. Generated from the code and checked by a test; regenerate with `./gradlew :app-boot:test --tests '*OpenApiContractTest' -Dopenapi.update=true`. |
| `openapi/billing-tenant-system.yaml` | The same API filtered to the operations a Tenant System may call with its API key, generated from the same document by the same test. Never edit it by hand. |
| `events/` | One JSON Schema per outbound event, plus how a webhook delivery is signed and verified. See [events/README.md](events/README.md). |

See [docs/architecture.md](../docs/architecture.md).
