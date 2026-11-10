plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract (scope, actor, permissions, idempotency, audit, shared errors) and the customer lookup.
    "domainImplementation"(project(":platform"))
    "applicationImplementation"(project(":platform"))
    "applicationImplementation"(project(":customer"))
    "adapterImplementation"(project(":platform"))
    "adapterImplementation"(project(":customer"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.assertj:assertj-core")
}
