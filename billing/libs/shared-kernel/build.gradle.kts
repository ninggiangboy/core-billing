plugins {
    id("java-conventions")
    `java-library`
}

// shared-kernel is plain Java: main depends on nothing but the JDK, so purity is a classpath fact.
dependencies {
    // Every layer that sees shared-kernel sees the kernel too, so a port never has to be re-exported by hand.
    api("dev.ngb:core-kernel")
    testImplementation("org.assertj:assertj-core")
}
