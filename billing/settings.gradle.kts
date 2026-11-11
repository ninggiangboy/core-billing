pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

rootProject.name = "core-billing"

include("shared-kernel", "jooq-schema", "contexts:platform", "contexts:customer", "contexts:money", "contexts:webhook", "contexts:usage", "contexts:pricing", "contexts:payment", "contexts:invoicing", "app-boot", "arch-fixtures")
