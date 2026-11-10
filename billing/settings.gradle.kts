pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

rootProject.name = "core-billing"

include("shared-kernel", "platform", "customer", "money", "webhook", "usage", "pricing", "app-boot", "arch-fixtures")
