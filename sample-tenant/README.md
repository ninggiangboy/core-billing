# sample-tenant

A reference Cloud Resource Management tenant. It is its own Gradle build and database and
integrates with Core Billing only over REST and signed webhooks; it shares no code with
`billing/`. See [docs/sample-tenant-system.md](../docs/sample-tenant-system.md).

The build convention is the one Billing proved: each module is a Gradle project whose `api`,
`domain`, `application` and `adapter` layers are separate source sets, so Spring cannot leak
into the plain-Java inner layers. `./gradlew checkNoBillingDependency` fails if any module
declares a Gradle dependency on Billing.
