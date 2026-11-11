plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract, the customer lookup, and the contracts of the contexts an invoice is built from and paid through:
    // money (wallets and charges), payment (starting an invoice payment) and pricing (billing cycles and renewal prices).
    "domainImplementation"(project(":platform"))
    "domainImplementation"(project(":pricing"))
    "applicationImplementation"(project(":platform"))
    "applicationImplementation"(project(":customer"))
    "applicationImplementation"(project(":money"))
    "applicationImplementation"(project(":payment"))
    "applicationImplementation"(project(":pricing"))
    "adapterImplementation"(project(":platform"))
    "adapterImplementation"(project(":customer"))
    "adapterImplementation"(project(":money"))
    "adapterImplementation"(project(":payment"))
    "adapterImplementation"(project(":pricing"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":jooq-schema"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.assertj:assertj-core")
}
