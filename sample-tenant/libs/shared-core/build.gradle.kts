plugins {
    id("java-conventions")
}

// shared-core is plain Java: main depends on nothing but the JDK, so purity is a classpath fact.
dependencies {
    testImplementation("org.assertj:assertj-core")
}
