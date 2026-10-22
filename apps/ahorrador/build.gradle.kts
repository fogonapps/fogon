plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.fogonapps.ahorrador"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.fogonapps.ahorrador"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
    }

    // Llave de firma: fuera del repo, en ~/.fogon/firma (o donde diga la propiedad fogonFirma).
    // Si no existe, el APK de publicación sale sin firmar.
    val dirFirma = providers.gradleProperty("fogonFirma").orElse(System.getProperty("user.home") + "/.fogon/firma").get()
    val llave = file("$dirFirma/fogon-firma.p12")
    val clave = file("$dirFirma/clave.txt")
    if (llave.exists() && clave.exists()) {
        signingConfigs {
            create("fogon") {
                storeFile = llave
                storeType = "PKCS12"
                storePassword = clave.readText().trim()
                keyAlias = "fogon"
                keyPassword = clave.readText().trim()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("fogon")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin el commit dentro del APK: el repo público tiene otros commits que el de trabajo,
            // y el APK tiene que salir idéntico al compilarlo desde cualquiera de los dos (F-Droid).
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    // F-Droid y compilaciones reproducibles: sin bloque de dependencias firmado por Google
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:identidad"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
