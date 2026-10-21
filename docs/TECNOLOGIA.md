# Tecnología

Elegida para que las apps sean rápidas, privadas, duraderas y aptas para F-Droid.

## La base: Android nativo con Kotlin + Jetpack Compose

- **Kotlin** es el lenguaje oficial de Android.
- **Jetpack Compose** es la forma recomendada por Google para hacer interfaces. Desde mayo de 2026 Google trabaja "Compose first": todo lo nuevo sale para Compose y el sistema anterior (Views) solo recibe arreglos.
- **Material 3**, pero con los colores de la identidad de Fogón (papel, tinta y las dos tintas de cada app). No usa *Material You* (los colores del fondo de pantalla) porque la identidad tiene que reconocerse de un vistazo.
- **Por qué nativo y no web o multiplataforma:** es lo más rápido, la app pesa menos, funciona sin conexión, accede bien al sistema (notificaciones, widgets, compartir) y es justo lo que usa F-Droid para su propia app desde la versión 2.0.

## Reglas técnicas

| Regla | Por qué |
|-------|---------|
| Sin Google Play Services, Firebase, AdMob ni analíticas | Privacidad y requisito de F-Droid |
| Datos en el teléfono (Room/SQLite o DataStore) | *Local-first*: tus datos son tuyos |
| Exportar e importar en formato abierto (JSON/CSV) | Nada de quedar atrapado en la app |
| Sin permiso `INTERNET` salvo que la app lo necesite de verdad | Garantía visible de que no envía datos |
| Solo bibliotecas libres compatibles con GPL-3.0 | Licencia limpia |
| Compilación reproducible con Gradle y sin binarios precompilados | F-Droid puede compilarla y verificarla |
| Español primero e inglés después (con `strings.xml`) | Para que la use más gente |
| `minSdk` razonable (Android 8 o superior) | Que funcione también en teléfonos viejos |

## Cómo se construye

1. **Idea:** una necesidad real que hoy se resuelve con una app llena de anuncios.
2. **Especificación:** en el `README.md` de la app se escribe qué hace y qué no hace, y en su `ARQUITECTURA.md` cómo está hecha.
3. **Código:** lo escribe la IA (Claude Code) y una persona lo revisa.
4. **Pruebas:** pruebas automáticas y luego en un teléfono real (el Samsung S24).
5. **Instalación:** desde el PC con `adb install`.
6. **Publicación** (más adelante): GitHub Releases y F-Droid. Cómo se firma y se entrega hoy: [`PUBLICAR.md`](PUBLICAR.md).

## Cómo está organizado el código

Es un solo repositorio (monorepo) con Gradle:

| Módulo | Qué es |
|--------|--------|
| `core/identidad` | La identidad de Fogón para Compose: colores, letras (recortadas a latín y español), personajes, botones con sombra riso, grano de papel (AGSL, Android 13+), pantalla vacía, chispa y "Acerca de". Todas las apps lo usan. |
| `apps/forzudo` | El Forzudo (antes Mi Gym). |

Decisiones de El Forzudo que repetirán las demás apps (detalle en [`ARQUITECTURA.md`](ARQUITECTURA.md)):
- **Datos:** un archivo JSON guardado con DataStore, con escrituras atómicas y una copia de respaldo si el archivo se daña. El formato es compatible con la versión anterior de cada app.
- **Secretos** (como el token de GitHub): cifrados con una llave AES-GCM del Keystore de Android.
- **Red:** OkHttp (Apache-2.0), solo para funciones opcionales.
- **Respaldo:** sin copias a la nube de Google; exportar e importar con el selector de archivos del sistema, sin permisos de almacenamiento.
- **Pruebas:** pruebas de JVM y, cuando se reescribe algo existente, una prueba de equivalencia contra la versión anterior.
- **Calidad:** el lint de Android sin errores antes de cada versión.

## Herramientas en el PC (Arch Linux)

Instaladas el 11 de octubre de 2026:

| Herramienta | Versión | Dónde |
|-------------|---------|-------|
| JDK | 21 (`jdk21-openjdk`) | `/usr/lib/jvm/java-21-openjdk` (el Java por defecto del sistema sigue siendo el 27) |
| SDK de Android | plataforma 37, build-tools 36 y 37 | `~/Android/Sdk` |
| Plugin de Android para Gradle | 9.4.1 (con Kotlin integrado, Kotlin 2.4.21) | descargado por Gradle |
| Gradle | 9.8.1 (wrapper con checksum fijado) | `~/.gradle` |
| Emulador | Android 16 (imagen AOSP sin servicios de Google), perfil `fogon-prueba` parecido al S24 | `~/Android/Sdk/emulator`, `~/.android/avd/` |

Compilar: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:forzudo:assembleDebug`.
