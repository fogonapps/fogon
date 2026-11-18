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

// Las pruebas visuales usan la tarea habitual, con su propio filtro y salida recuperable de la caché.
subprojects {
    if (path.startsWith(":apps:")) {
        val soloCapturas = providers.gradleProperty("soloCapturas").isPresent
        val directorioCapturas = layout.buildDirectory.dir("reports/capturas-pantallas")
        val homePruebas = gradle.gradleUserHomeDir
        tasks.withType<Test>().configureEach {
            if (soloCapturas) outputs.dir(directorioCapturas)
            // Robolectric necesita un bloqueo de escritura junto al directorio de usuario.
            systemProperty("user.home", homePruebas)
            systemProperty("maven.repo.local", file("${System.getProperty("user.home")}/.m2/repository"))
        }
    }
}
