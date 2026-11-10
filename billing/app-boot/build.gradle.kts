plugins {
    id("boot-conventions")
}

dependencies {
    implementation(project(":shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter")
}
