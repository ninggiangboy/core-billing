plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract (scope, actor, permissions, idempotency, audit, shared errors) and the customer lookup.
    "domainImplementation"(project(":contexts:platform"))
    "applicationImplementation"(project(":contexts:platform"))
    "applicationImplementation"(project(":contexts:customer"))
    "adapterImplementation"(project(":contexts:platform"))
    "adapterImplementation"(project(":contexts:customer"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":libs:jooq-schema"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.assertj:assertj-core")
}
