plugins {
    id("layered-module-conventions")
}

dependencies {
    "adapterImplementation"(platform(libs.spring.cloud.bom))
    "adapterImplementation"(libs.spring.cloud.starter.openfeign)
    "adapterImplementation"(libs.feign.java11)
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jackson")
    testImplementation("org.assertj:assertj-core")
}
