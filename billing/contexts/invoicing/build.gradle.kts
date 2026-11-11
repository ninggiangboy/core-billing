plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract, the customer lookup, and the contracts of the contexts an invoice is built from and paid through:
    // money (wallets and charges), payment (starting an invoice payment) and pricing (billing cycles and renewal prices).
    "domainImplementation"(project(":contexts:platform"))
    "domainImplementation"(project(":contexts:pricing"))
    "applicationImplementation"(project(":contexts:platform"))
    "applicationImplementation"(project(":contexts:customer"))
    "applicationImplementation"(project(":contexts:money"))
    "applicationImplementation"(project(":contexts:payment"))
    "applicationImplementation"(project(":contexts:pricing"))
    "adapterImplementation"(project(":contexts:platform"))
    "adapterImplementation"(project(":contexts:customer"))
    "adapterImplementation"(project(":contexts:money"))
    "adapterImplementation"(project(":contexts:payment"))
    "adapterImplementation"(project(":contexts:pricing"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":jooq-schema"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.assertj:assertj-core")
}
