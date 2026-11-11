plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.fogonapps.social.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":core:identidad"))
    api(project(":core:perfil"))
    api(project(":core:social"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.work.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
}
