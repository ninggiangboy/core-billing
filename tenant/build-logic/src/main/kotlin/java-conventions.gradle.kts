import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
}

val catalog = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain { languageVersion = JavaLanguageVersion.of(catalog.findVersion("jdk").get().requiredVersion.toInt()) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = catalog.findVersion("jdk").get().requiredVersion.toInt()
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

dependencies {
    // One version authority for every configuration that resolves Spring-managed libraries.
    val bom = platform(catalog.findLibrary("spring-boot-bom").get())
    listOf("implementation", "testImplementation", "testRuntimeOnly").forEach { add(it, bom) }
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
