plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract: scope, actor, permissions, idempotency, audit and the shared error types.
    "domainImplementation"(project(":platform"))
    "applicationImplementation"(project(":platform"))
    "adapterImplementation"(project(":platform"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":jooq-schema"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.assertj:assertj-core")
}
