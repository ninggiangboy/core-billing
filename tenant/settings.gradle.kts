pluginManagement {
    includeBuild("build-logic")
}

includeBuild("../core")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

rootProject.name = "tenant"

include(
    "libs:shared-core",
    "libs:jooq-schema",
    "modules:identity",
    "modules:customer",
    "modules:catalog",
    "modules:resource",
    "modules:execution",
    "modules:usage",
    "modules:billing-client",
    "modules:webhook",
    "apps:app-boot",
    "testing:arch-fixtures",
)
