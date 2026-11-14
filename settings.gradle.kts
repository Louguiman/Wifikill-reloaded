pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wifikill-reloaded"

include(
    ":app",
    ":core:common",
    ":core:domain",
    ":core:database",
    ":core:network",
    ":core:security",
    ":core:designsystem",
    ":core:testing",
    ":data:repository",
    ":feature:dashboard",
    ":feature:discovery",
    ":feature:devices",
    ":feature:monitoring",
    ":feature:accessmanagement",
    ":feature:diagnostics",
    ":feature:routermanagement",
    ":feature:history",
    ":feature:settings",
    ":integrations:router-api",
    ":integrations:openwrt",
    ":integrations:mikrotik",
    ":integrations:generic-router",
    ":integrations:root",
    ":background:workers",
)
