plugins {
    id("layered-module-conventions")
}

dependencies {
    "applicationImplementation"(project(":modules:billing-client"))
    "adapterImplementation"(project(":modules:billing-client"))
    "adapterImplementation"(project(":modules:customer"))
    "adapterImplementation"(project(":modules:identity"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    "adapterImplementation"("org.springframework.security:spring-security-core")
    testImplementation("org.assertj:assertj-core")
}
