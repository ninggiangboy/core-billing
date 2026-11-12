plugins {
    id("java-conventions")
}

// Deliberately violating classes. They are on the test classpath of app-boot only, never on the
// application classpath, and exist to prove that each architecture rule can fail the build.
dependencies {
    compileOnly("org.springframework:spring-context")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
}
