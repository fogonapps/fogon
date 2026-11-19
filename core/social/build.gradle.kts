plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.fogonapps.social"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { buildConfig = true }
    val produccion = "https://fogonapps.duckdns.org"
    buildTypes {
        debug {
            val servidor = providers.gradleProperty("servidorSocial").orElse(produccion).get().trimEnd('/')
            require(servidor == produccion || servidor == "http://localhost:8080") {
                "servidorSocial solo admite producción o http://localhost:8080"
            }
            buildConfigField("String", "SERVIDOR_SOCIAL", "\"$servidor\"")
        }
        release { buildConfigField("String", "SERVIDOR_SOCIAL", "\"$produccion\"") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.okhttp)
    implementation(libs.tink.android)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
