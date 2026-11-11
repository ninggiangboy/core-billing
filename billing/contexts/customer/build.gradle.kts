plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract: scope, actor, permissions, idempotency, audit and the shared error types.
    "domainImplementation"(project(":contexts:platform"))
    "applicationImplementation"(project(":contexts:platform"))
    "adapterImplementation"(project(":contexts:platform"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":libs:jooq-schema"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.assertj:assertj-core")
}
