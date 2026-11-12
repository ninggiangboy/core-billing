plugins {
    id("layered-module-conventions")
}

dependencies {
    "applicationImplementation"(project(":modules:billing-client"))
    "applicationImplementation"(project(":modules:customer"))
    "applicationImplementation"(project(":modules:catalog"))
    "adapterImplementation"(project(":modules:billing-client"))
    "adapterImplementation"(project(":modules:customer"))
    "adapterImplementation"(project(":modules:catalog"))
    "adapterImplementation"(project(":modules:identity"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    "adapterImplementation"("org.springframework.security:spring-security-core")
    testImplementation("org.assertj:assertj-core")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":libs:jooq-schema"))
}
