plugins {
    id("boot-conventions")
}

dependencies {
    implementation(platform(libs.spring.modulith.bom))
    implementation(project(":shared-kernel"))
    implementation(project(":platform"))
    implementation(project(":customer"))
    implementation(project(":money"))
    implementation(project(":webhook"))
    implementation(project(":usage"))
    implementation(project(":pricing"))
    implementation(project(":payment"))
    implementation(project(":invoicing"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation(libs.springdoc.webmvc)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":arch-fixtures"))
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.modulith.core)
    testImplementation(libs.spring.modulith.docs)
    testImplementation(libs.json.schema.validator)
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

// The event schemas under contracts/events are the source of truth for which event types exist; the
// application reads them from the classpath, so the catalog and the contract cannot drift apart.
tasks.processResources {
    from(layout.projectDirectory.dir("../../contracts/events")) {
        include("*.schema.json")
        into("events")
    }
}

tasks.test {
    // `./gradlew :app-boot:test -Dopenapi.update=true` rewrites contracts/openapi/billing.yaml.
    systemProperty("openapi.update", System.getProperty("openapi.update", "false"))
}
