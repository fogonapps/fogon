plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

group = "io.github.fogonapps"
version = "0.49"

kotlin { jvmToolchain(21) }

val ktor = "3.6.0"
dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor")
    implementation("io.ktor:ktor-server-cio-jvm:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktor")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.eclipse.angus:angus-mail:2.0.5")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor")
    testImplementation(kotlin("test"))
}

application { mainClass.set("io.github.fogonapps.social.PrincipalKt") }

// Las pruebas crean carpetas temporales: van a ~/.cache (disco, fuera del repo; /tmp vive en la RAM) y se borran siempre al terminar, aunque fallen.
val temporalPruebas = File(System.getProperty("user.home"), ".cache/fogon-pruebas-servidor")
val limpiarTemporalPruebas by tasks.registering { doLast { temporalPruebas.deleteRecursively() } }
tasks.test {
    useJUnitPlatform()
    systemProperty("java.io.tmpdir", temporalPruebas.absolutePath)
    doFirst { temporalPruebas.deleteRecursively(); temporalPruebas.mkdirs() }
    finalizedBy(limpiarTemporalPruebas)
}

