plugins {
    id("layered-module-conventions")
}

dependencies {
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    testImplementation("org.assertj:assertj-core")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":libs:jooq-schema"))
}
