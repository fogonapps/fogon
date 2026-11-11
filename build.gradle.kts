plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Guava llega por Media3 y otras bibliotecas. Se fija en una versión sin el aviso GHSA-xxph-c9ww-hj94:
// las apps no usan la parte afectada, pero así no queda pendiente.
subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.google.guava" && requested.name == "guava") useVersion("33.8.0-android")
        }
    }
}
