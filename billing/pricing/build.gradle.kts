plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract, the customer lookup, and the read-only edges to other contexts: usage quantities, wallets, charging and reserving.
    "domainImplementation"(project(":platform"))
    "applicationImplementation"(project(":platform"))
    "applicationImplementation"(project(":customer"))
    "applicationImplementation"(project(":usage"))
    "applicationImplementation"(project(":money"))
    "adapterImplementation"(project(":platform"))
    "adapterImplementation"(project(":customer"))
    "adapterImplementation"(project(":usage"))
    "adapterImplementation"(project(":money"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jackson")
    testImplementation("org.assertj:assertj-core")
}
