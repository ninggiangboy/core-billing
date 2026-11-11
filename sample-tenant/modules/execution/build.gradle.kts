plugins {
    id("layered-module-conventions")
}

dependencies {
    "applicationImplementation"(project(":modules:billing-client"))
    "applicationImplementation"(project(":modules:resource"))
    "applicationImplementation"(project(":modules:usage"))
    "adapterImplementation"(project(":modules:billing-client"))
    "adapterImplementation"(project(":modules:resource"))
    "adapterImplementation"(project(":modules:usage"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    testImplementation("org.assertj:assertj-core")
}
