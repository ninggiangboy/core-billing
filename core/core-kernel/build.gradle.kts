plugins {
    `java-library`
    id("java-conventions")
}

// core-kernel is plain Java: main depends on nothing but the JDK, so purity is a classpath fact.
dependencies {
    testImplementation("org.assertj:assertj-core")
}
