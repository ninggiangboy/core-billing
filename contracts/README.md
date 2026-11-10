# contracts

Documents that the two systems share, never code.

| Path | Owns |
|---|---|
| `openapi/billing.yaml` | The REST API. Generated from the code and checked by a test; regenerate with `./gradlew :app-boot:test --tests '*OpenApiContractTest' -Dopenapi.update=true`. |
| `events/` | One JSON Schema per outbound event, plus how a webhook delivery is signed and verified. See [events/README.md](events/README.md). |

See [docs/architecture.md](../docs/architecture.md).
