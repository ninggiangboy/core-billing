plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract (scope, actor, permissions, audit, shared errors).
    "domainImplementation"(project(":platform"))
    "applicationImplementation"(project(":platform"))
    "adapterImplementation"(project(":platform"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-data-jdbc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jackson")
    "adapterImplementation"("org.apache.httpcomponents.client5:httpclient5")
    testImplementation("org.assertj:assertj-core")
}
