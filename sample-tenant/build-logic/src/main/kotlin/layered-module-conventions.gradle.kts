/*
 * A module is one Gradle project with a published contract and three layers, each its own source set:
 *
 *   api          the contract other modules may use; sees only shared-core and the JDK
 *   domain       sees api, shared-core and the JDK
 *   application  sees domain, api and shared-core (plain Java, no framework)
 *   adapter      sees all of the above plus the framework
 *
 * Because the classpaths differ, importing Spring into domain or application is a compile
 * error rather than a convention. ArchUnit covers what a classpath cannot (package placement).
 */
plugins {
    id("java-conventions")
}

val catalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

val api = sourceSets.create("api")
val domain = sourceSets.create("domain")
val application = sourceSets.create("application")
val adapter = sourceSets.create("adapter")
val layers = listOf(api, domain, application, adapter)

if (project.name != "shared-core") {
    dependencies {
        layers.forEach { add(it.implementationConfigurationName, project(":libs:shared-core")) }
    }
}

// Layer direction: adapter -> application -> domain -> api.
domain.compileClasspath += api.output
domain.runtimeClasspath += api.output
application.compileClasspath += api.output + domain.output
application.runtimeClasspath += api.output + domain.output
adapter.compileClasspath += api.output + domain.output + application.output
adapter.runtimeClasspath += api.output + domain.output + application.output

// The Gradle `main` source set is unused; tests see every layer.
sourceSets.test {
    compileClasspath += api.output + domain.output + application.output + adapter.output
    runtimeClasspath += api.output + domain.output + application.output + adapter.output
}

// BOM-managed versions for the adapter layer; the inner layers get no framework BOM at all.
dependencies {
    add(adapter.implementationConfigurationName, platform(catalog.findLibrary("spring-boot-bom").get()))
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
