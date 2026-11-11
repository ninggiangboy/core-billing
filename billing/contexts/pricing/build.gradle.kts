plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract, the customer lookup, and the read-only edges to other contexts: usage quantities, wallets, charging and reserving.
    "domainImplementation"(project(":contexts:platform"))
    "applicationImplementation"(project(":contexts:platform"))
    "applicationImplementation"(project(":contexts:customer"))
    "applicationImplementation"(project(":contexts:usage"))
    "applicationImplementation"(project(":contexts:money"))
    "adapterImplementation"(project(":contexts:platform"))
    "adapterImplementation"(project(":contexts:customer"))
    "adapterImplementation"(project(":contexts:usage"))
    "adapterImplementation"(project(":contexts:money"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":jooq-schema"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jackson")
    testImplementation("org.assertj:assertj-core")
}
