pluginManagement {
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

rootProject.name = "fogon"

include(":core:identidad")
include(":core:perfil")
include(":core:social")
include(":core:social-ui")
include(":core:revision")
include(":apps:forzudo")
include(":apps:ahorrador")
include(":apps:cucha")
include(":apps:cajon")
