plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Solo para las pruebas de las apps: el recorrido de pantallas y su revisión (botones pegados o encimados, tamaño
// para el dedo, nombres para el lector de pantalla). No va dentro de ninguna APK.
android {
    testOptions { unitTests.isIncludeAndroidResources = true }
    namespace = "io.github.fogonapps.revision"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    testImplementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.test.manifest)
    api(platform(libs.compose.bom))
    api(libs.compose.ui.test.junit4)
    api(libs.robolectric)
    api(libs.junit)
    api(libs.work.testing)
    api(libs.kotlin.reflect)
}
