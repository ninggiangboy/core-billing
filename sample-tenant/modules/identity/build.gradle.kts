plugins {
    id("layered-module-conventions")
}

dependencies {
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    "adapterImplementation"("org.springframework.security:spring-security-crypto")
    "adapterImplementation"("org.springframework.security:spring-security-core")
    testImplementation("org.assertj:assertj-core")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":libs:jooq-schema"))
}
