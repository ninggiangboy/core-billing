plugins {
    id("layered-module-conventions")
}

dependencies {
    "applicationImplementation"(project(":modules:resource"))
    "adapterImplementation"(project(":modules:resource"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jackson")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    testImplementation("org.assertj:assertj-core")
}
