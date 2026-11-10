/*
 * A bounded context is one Gradle module with three layers, each its own source set:
 *
 *   domain       sees only shared-kernel and the JDK
 *   application  sees domain and shared-kernel (plain Java, no framework)
 *   adapter      sees domain, application and the framework
 *
 * Because the classpaths differ, importing Spring into domain or application is a compile
 * error rather than a convention. ArchUnit covers what a classpath cannot (cross-context
 * imports, package placement).
 */
plugins {
    id("java-conventions")
}

val catalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

val domain = sourceSets.create("domain")
val application = sourceSets.create("application")
val adapter = sourceSets.create("adapter")
val layers = listOf(domain, application, adapter)

if (project.name != "shared-kernel") {
    dependencies {
        layers.forEach { add(it.implementationConfigurationName, project(":shared-kernel")) }
    }
}

// Layer direction: application -> domain, adapter -> application + domain.
application.compileClasspath += domain.output
application.runtimeClasspath += domain.output
adapter.compileClasspath += domain.output + application.output
adapter.runtimeClasspath += domain.output + application.output

// The Gradle `main` source set is unused; tests see every layer.
sourceSets.test {
    compileClasspath += domain.output + application.output + adapter.output
    runtimeClasspath += domain.output + application.output + adapter.output
}

// BOM-managed versions for the adapter layer; the inner layers get no framework BOM at all.
dependencies {
    add(adapter.implementationConfigurationName, platform(catalog.findLibrary("spring-boot-bom").get()))
    add(adapter.implementationConfigurationName, platform(catalog.findLibrary("spring-modulith-bom").get()))
    // Lets an adapter-side package-info.java declare the module's `api` package a Spring Modulith
    // named interface without putting a framework annotation into the plain-Java application layer.
    add(adapter.implementationConfigurationName, catalog.findLibrary("spring-modulith-api").get())
}

// Test configurations inherit the adapter's framework dependencies so adapter tests compile.
configurations {
    named(sourceSets.test.get().implementationConfigurationName) { extendsFrom(configurations[adapter.implementationConfigurationName]) }
    named(sourceSets.test.get().runtimeOnlyConfigurationName) { extendsFrom(configurations[adapter.runtimeOnlyConfigurationName]) }
    // Consumers (other modules, app-boot) resolve the adapter's runtime dependencies transitively.
    named("implementation") { extendsFrom(configurations[adapter.implementationConfigurationName]) }
    named("runtimeOnly") { extendsFrom(configurations[adapter.runtimeOnlyConfigurationName]) }
}

// One jar per module containing all layers. Consumers resolve that jar, not the empty `main` classes.
tasks.jar { from(layers.map { it.output }) }
tasks.named("compileJava") { dependsOn(layers.map { it.classesTaskName }) }
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) { outgoing.variants.clear() }
}
