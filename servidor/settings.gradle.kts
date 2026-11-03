// Fogón Social: el servidor de los perfiles y amigos de El Cajón. Es un build aparte (no entra al de las apps):
// se compila con `../gradlew -p servidor build`.
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "fogon-social"
