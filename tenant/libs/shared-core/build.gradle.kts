plugins {
    id("java-conventions")
    `java-library`
}

// shared-core is plain Java: main depends on nothing but the JDK and the shared core kernel.
dependencies {
    // Every layer that sees shared-core sees the kernel too, so a port never has to be re-exported by hand.
    api("dev.ngb:core-kernel")
    testImplementation("org.assertj:assertj-core")
}
