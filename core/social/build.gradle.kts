plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.fogonapps.social"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.okhttp)
    implementation(libs.tink.android)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
