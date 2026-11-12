plugins {
    `java-library`
    id("java-conventions")
}

// The Spring implementation of the runtime ports. Only this module may see a framework type.
dependencies {
    api(project(":core-kernel"))
    api("org.springframework:spring-tx")
    testImplementation("org.assertj:assertj-core")
}
