plugins {
    id("boot-conventions")
}

dependencies {
    implementation(platform(libs.spring.modulith.bom))
    implementation(project(":shared-kernel"))
    implementation(project(":platform"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":arch-fixtures"))
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.modulith.core)
    testImplementation(libs.spring.modulith.docs)
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
