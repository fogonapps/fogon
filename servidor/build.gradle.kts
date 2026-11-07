plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

group = "io.github.fogonapps"
version = "0.26"

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

tasks.test { useJUnitPlatform() }
