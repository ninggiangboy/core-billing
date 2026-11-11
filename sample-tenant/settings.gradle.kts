pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

rootProject.name = "sample-tenant"

include(
    "libs:shared-core",
    "modules:identity",
    "modules:customer",
    "modules:catalog",
    "modules:resource",
    "modules:execution",
    "modules:usage",
    "modules:billing-client",
    "modules:webhook-receiver",
    "apps:app-boot",
)
