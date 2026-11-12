import org.gradle.api.artifacts.ProjectDependency

// Root project holds no code. Shared build logic lives in build-logic/.

allprojects {
    group = "dev.ngb"
}

/*
 * The two systems share contracts and the technical core, never Billing code. This check fails the
 * build when a sample-tenant project declares a Gradle dependency on a Billing project. The core is
 * included as a separate build and found by its coordinates, so it does not trip the check.
 */
tasks.register("checkNoBillingDependency") {
    group = "verification"
    description = "Fails when a sample-tenant project declares a Gradle dependency on Billing."
    doLast {
        val offenders = allprojects.flatMap { project ->
            project.configurations.flatMap { configuration ->
                configuration.dependencies.mapNotNull { dependency ->
                    (dependency as? ProjectDependency)
                        ?.takeIf { it.path.split(":").any { segment -> segment == "billing" } }
                        ?.let { "${project.path}:${configuration.name} -> ${it.path}" }
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "The sample tenant must not depend on Billing at build time:\n" + offenders.joinToString("\n"),
            )
        }
    }
}
